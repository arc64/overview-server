package com.overviewdocs.messagequeue.apollo

import akka.actor._
import com.overviewdocs.messagequeue.{ MessageContainer, MessageReceiver }

/**
 * Helper for creating MessageReceivers listening to a given apollo Message queue
 */
object ApolloMessageReceiver {

  def apply[T](messageRecipient: ActorRef, 
               queueName: String, 
               messageConverter: String => T): Props = {
    val messageService = new ApolloMessageService(queueName)
    
    Props(new MessageReceiver[T](messageRecipient, messageService, messageConverter))
  }
}