package com.overviewdocs.messagequeue

import akka.actor._
import akka.testkit._

import com.overviewdocs.jobhandler.JobProtocol._
import com.overviewdocs.messagequeue.ConnectionMonitorProtocol._
import com.overviewdocs.messagequeue.MessageHandlerProtocol._
import com.overviewdocs.messagequeue.AcknowledgingMessageReceiverProtocol._
import com.overviewdocs.messagequeue.MessageQueueConnectionProtocol._
import com.overviewdocs.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mock.Mockito
import org.specs2.mutable.{ Before, Specification }

import javax.jms.Connection

class AcknowledgingMessageReceiverSpec extends Specification with Mockito {

  class TestMessageService extends MessageService {
    var currentConnection: Connection = _
    var deliverMessage: MessageContainer => Unit = _
    var lastAcknowledged: Option[MessageContainer] = None

    override def listenToConnection(connection: Connection, messageDelivery: MessageContainer => Unit): Unit = {
      currentConnection = connection
      deliverMessage = messageDelivery
    }

    override def acknowledge(message: MessageContainer): Unit = lastAcknowledged = Some(message)
    override def stopListening: Unit = {
      currentConnection = null
    }
  }

  class TestMessageQueueActor(messageHandler: ActorRef, messageService: MessageService) extends AcknowledgingMessageReceiver[String](messageService) {
    override def createMessageHandler: Props = Props(new ForwardingActor(messageHandler))
    override def convertMessage(message: String): String = s"CONVERTED$message"
  }

  "AcknowledgingMessageReceiver" should {

    trait MessageServiceProvider {
      val connection = smartMock[Connection]
      val messageService: MessageService
    }

    abstract class MessageQueueActorSetup extends ActorSystemContext with Before {
      self: MessageServiceProvider =>
      var messageHandler: TestProbe = _
      var messageQueueActor: TestActorRef[TestMessageQueueActor] = _

      def before = {
        messageHandler = TestProbe()
        messageQueueActor = TestActorRef(new TestMessageQueueActor(messageHandler.ref, messageService))
      }
    }

    trait MockedMessageService extends MessageServiceProvider {
      override val messageService = smartMock[MessageService]
    }

    trait FakeMessageService extends MessageServiceProvider {
      val testMessageService = new TestMessageService
      val messageService = testMessageService

      val messageText = "a message"
      val message = smartMock[MessageContainer]
      message.text returns messageText

    }

    "register itself with connection monitor" in new MessageQueueActorSetup with MockedMessageService {
      val connectionMonitor = TestProbe()

      messageQueueActor ! RegisterWith(connectionMonitor.ref)

      connectionMonitor.expectMsg(RegisterClient)
    }

    "start listening to incoming connection" in new MessageQueueActorSetup with MockedMessageService {
      messageQueueActor ! ConnectedTo(connection)

      there was one(messageService).listenToConnection(any, any)
    }

    "send incoming message to listener" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)

      testMessageService.deliverMessage(message)
      messageHandler.expectMsg(s"CONVERTED$messageText")
    }

    "acknowledge message when handled by listener" in new MessageQueueActorSetup with FakeMessageService {

      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)
      messageQueueActor ! MessageHandled

      messageService.lastAcknowledged must beSome(message)
    }
    
    "queue incoming messages when listener is busy" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)
      
      messageHandler.expectMsg(s"CONVERTED$messageText")
      
      testMessageService.deliverMessage(message)
      
      messageQueueActor ! MessageHandled
      messageHandler.expectMsg(s"CONVERTED$messageText")
      
    }

    "ignore message handled from listener when connection has failed" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)

      messageQueueActor ! ConnectionFailed
      messageQueueActor ! MessageHandled

      messageService.lastAcknowledged must beNone
    }

    "ignore message handled from listener when new connection has been re-established" in new MessageQueueActorSetup with FakeMessageService {
      val newConnection = mock[Connection]

      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)

      messageQueueActor ! ConnectionFailed
      messageQueueActor ! ConnectedTo(newConnection)
      messageQueueActor ! MessageHandled

      messageService.lastAcknowledged must beNone
      messageService.currentConnection must be equalTo (newConnection)

    }

    "handle repeated connection failures while message handler is busy" in new MessageQueueActorSetup with FakeMessageService {
      val newConnection1 = mock[Connection]
      val newConnection2 = mock[Connection]

      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)

      messageQueueActor ! ConnectionFailed
      messageQueueActor ! ConnectedTo(newConnection1)
      messageQueueActor ! ConnectionFailed
      messageQueueActor ! ConnectedTo(newConnection2)
      messageQueueActor ! MessageHandled

      messageService.currentConnection must be equalTo (newConnection2)
    }

    "close message service when connection fails " in new MessageQueueActorSetup with MockedMessageService {
      messageQueueActor ! ConnectedTo(connection)
      messageQueueActor ! ConnectionFailed

      there was one(messageService).stopListening
    }

    "close message service when connection fails while message handler is busy" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)

      messageQueueActor ! ConnectionFailed

      testMessageService.currentConnection must beNull
    }
  }
}