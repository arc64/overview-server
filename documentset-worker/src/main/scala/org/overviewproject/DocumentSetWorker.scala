package org.overviewproject

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.database.{ DataSource, DB, DatabaseConfiguration }
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandler
import org.overviewproject.jobhandler.filegroup._
import org.overviewproject.messagequeue.AcknowledgingMessageReceiverProtocol._
import org.overviewproject.messagequeue.MessageQueueConnectionProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageQueueConnection
import org.overviewproject.util.Logger
import org.overviewproject.background.filecleanup.{ DeletedFileCleaner, FileCleaner, FileRemovalRequestQueue }
import org.overviewproject.background.filegroupcleanup.{ DeletedFileGroupCleaner, FileGroupCleaner, FileGroupRemovalRequestQueue }

object ActorCareTakerProtocol {
  case object StartListening
}

/**
 * Creates as many DocumentSetJobHandler actors as we think we can handle, with a shared
 * RequestQueue. DocumentSetJobHandlers handle request specific to DocumentSets, after clustering
 * has started (such as Search and Delete)
 * Each DocumentSetJobHandler is responsible for listening to the message queue, and spawning
 * handlers for incoming commands.
 * We assume that the queue is setup with message groups, so that messages for a
 * particular document set are sent to one JobHandler only. At some point DocumentSetJobHandlers
 * may want to explicitly disconnect from the queue, to rebalance the message groups.
 * Also creates a ClusteringJobHandler which manages processing of file uploads, up until the clustering
 * process starts.
 */
object DocumentSetWorker extends App {
  import ActorCareTakerProtocol._

  private val WorkerActorSystemName = "WorkerActorSystem"
  private val FileGroupJobQueueSupervisorName = "FileGroupJobQueueSupervisor"
  private val FileGroupJobQueueName = "FileGroupJobQueue"
  private val FileRemovalQueueName = "FileRemovalQueue"

  private val NumberOfJobHandlers = 8

  val config = DatabaseConfiguration.fromConfig
  val dataSource = DataSource(config)

  DB.connect(dataSource)

  implicit val system = ActorSystem(WorkerActorSystemName)
  val actorCareTaker = system.actorOf(
    ActorCareTaker(NumberOfJobHandlers, FileGroupJobQueueName, FileRemovalQueueName),
    FileGroupJobQueueSupervisorName)

  actorCareTaker ! StartListening

}

/**
 * Supervisor for the actors.
 * Creates the connection hosting the message queues, and tells
 * clients to register for connection status messages.
 * If an error occurs at this level, we assume that something catastrophic has occurred.
 * All actors get killed, and the process exits.
 */
class ActorCareTaker(numberOfJobHandlers: Int, fileGroupJobQueueName: String, fileRemovalQueueName: String) extends Actor {
  import ActorCareTakerProtocol._

  // FileGroup removal background worker
  private val fileGroupCleaner = createMonitoredActor(FileGroupCleaner(), "FileGroupCleaner")
  private val fileGroupRemovalRequestQueue = createMonitoredActor(FileGroupRemovalRequestQueue(fileGroupCleaner),
    "FileGroupRemovalRequestQueue")

  // deletedFileGroupRemover is not monitored, because it requests removals for
  // deleted FileGroups on startup, and then terminates.
  private val deletedFileGroupRemover =
    context.actorOf(DeletedFileGroupCleaner(fileGroupRemovalRequestQueue), "DeletedFileGroupRemover")

  // File removal background worker      
  private val fileCleaner = createMonitoredActor(FileCleaner(), "FileCleaner")
  private val deletedFileRemover = createMonitoredActor(DeletedFileCleaner(fileCleaner), "DeletedFileCleaner")
  private val fileRemovalQueue = createMonitoredActor(FileRemovalRequestQueue(deletedFileRemover), fileRemovalQueueName)

  private val connectionMonitor = createMonitoredActor(ApolloMessageQueueConnection(), "MessageQueueConnection")
  // Start as many job handlers as you need
  private val jobHandlers = Seq.tabulate(numberOfJobHandlers)(n =>
    createMonitoredActor(DocumentSetJobHandler(fileRemovalQueue.path.toString), s"DocumentSetJobHandler-$n"))

  private val progressReporter = createMonitoredActor(ProgressReporter(), "ProgressReporter")
  private val documentIdSupplier = createMonitoredActor(DocumentIdSupplier(), "DocumentIdSupplier")
  private val fileGroupJobQueue = createMonitoredActor(FileGroupJobQueue(progressReporter, documentIdSupplier),
      fileGroupJobQueueName)
      
  Logger.info(s"Job Queue path ${fileGroupJobQueue.path}")
  private val clusteringJobQueue = 
    createMonitoredActor(ClusteringJobQueue(fileGroupRemovalRequestQueue.path.toString), "ClusteringJobQueue")
  private val fileGroupJobQueueManager = createMonitoredActor(FileGroupJobManager(fileGroupJobQueue, clusteringJobQueue), "FileGroupJobManager")
  private val uploadClusteringCommandBridge = createMonitoredActor(ClusteringCommandsMessageQueueBridge(fileGroupJobQueueManager), "ClusteringCommandsMessageQueueBridge")

  private val taskWorkerSupervisor = createMonitoredActor(
    FileGroupTaskWorkerStartup(
      fileGroupJobQueue.path.toString,
      fileRemovalQueue.path.toString,
      fileGroupRemovalRequestQueue.path.toString), "TaskWorkerSupervisor")

  /**
   *   A more optimistic approach would be to simply restart the actor. At the moment, we don't know
   *   enough about the error modes to know whether an actor restart would be successful.
   *   Instead, we stop everything and exit the process. On production, the process will be automatically
   *   restarted.
   */
  override def supervisorStrategy = AllForOneStrategy(0, Duration.Inf) {
    case _ => stop
  }

  def receive = {
    case StartListening => {
      uploadClusteringCommandBridge ! RegisterWith(connectionMonitor)
      jobHandlers.foreach(_ ! RegisterWith(connectionMonitor))
      connectionMonitor ! StartConnection
    }
    case Terminated(a) => {
      Logger.error("Unexpected shutdown")
      context.system.shutdown
      System.exit(-1)
    }
  }

  private def createMonitoredActor(props: Props, name: String): ActorRef = {
    val monitee = context.actorOf(props, name) // like manatee? get it?
    context.watch(monitee)
  }
}

object ActorCareTaker {
  def apply(numberOfJobHandlers: Int, fileGroupJobQueueName: String, fileRemovalQueueName: String): Props =
    Props(new ActorCareTaker(numberOfJobHandlers, fileGroupJobQueueName, fileRemovalQueueName))
}

