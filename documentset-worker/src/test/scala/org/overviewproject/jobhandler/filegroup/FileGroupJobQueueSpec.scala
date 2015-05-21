package org.overviewproject.jobhandler.filegroup

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.overviewproject.jobhandler.filegroup.FileGroupJobQueueProtocol._
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol._
import akka.testkit._
import org.specs2.time.NoTimeConversions
import akka.actor.ActorRef
import akka.actor.ActorSystem
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import akka.actor.Terminated
import akka.actor.PoisonPill
import akka.actor.Actor
import akka.actor.Props
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions

class FileGroupJobQueueSpec extends Specification with NoTimeConversions {

  "FileGroupJobQueue" should {

    "notify registered workers when tasks becomes available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      submitJob(documentSetId)

      worker.expectMsg(TaskAvailable)
    }

    "send available tasks to workers that ask for them" in new JobQueueContext {
      val workers = createNWorkers(numberOfUploadedFiles)
      workers.foreach(w => fileGroupJobQueue ! RegisterWorker(w.ref))

      submitJob(documentSetId)
      val receivedTasks = expectTasks(workers)
      mustMatchUploadedFileIds(receivedTasks, uploadedFileIds)
    }

    "notify worker if tasks are available when it registers" in new JobQueueContext {
      submitJob(documentSetId)
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      worker.expectMsg(TaskAvailable)
    }

    "don't send anything if no tasks are available" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      fileGroupJobQueue.tell(ReadyForTask, worker.ref)

      worker.expectNoMsg(50 millis)
    }

    "notify requester when all tasks for a fileGroupId are complete" in new JobQueueContext {
      ActAsImmediateJobCompleter(worker, fileId)
      submitJob(documentSetId)

      fileGroupJobQueue ! RegisterWorker(worker.ref)

      expectMsg(JobCompleted(documentSetId))
    }

    "ignore a second job for the same fileGroup" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      submitJob(documentSetId)
      worker.expectMsg(TaskAvailable)

      submitJob(documentSetId)
      worker.expectNoMsg(50 millis)

    }


    "cancel running tasks" in new JobQueueContext {
      val workers = createNWorkers(numberOfUploadedFiles / 2)
      workers.foreach(w => fileGroupJobQueue ! RegisterWorker(w.ref))

      submitJob(documentSetId)
      val receivedTasks = expectTasks(workers)

      fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)

      expectCancellation(workers)

      workers.head.expectNoTaskAvailable(fileGroupJobQueue)
    }

    "notify requester when cancellation is complete" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      submitJob(documentSetId)
      val task = worker.expectATask

      fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)
      
      worker.completeCancelledTask(task.uploadedFileId)

      expectMsg(JobCompleted(documentSetId))
    }

    "cancel not started job" in new JobQueueContext {
      submitJob(documentSetId)
      
      fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)
      
      expectMsg(JobCompleted(documentSetId))
    }
    
    "delete file upload" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      fileGroupJobQueue ! SubmitJob(documentSetId, DeleteFileGroupJob(fileGroupId))

      worker.expectDeleteFileUploadJob
    }

    "notify requester when file upload is deleted" in new JobQueueContext {
      ActAsImmediateJobCompleter(worker, fileId)
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      
      fileGroupJobQueue ! SubmitJob(documentSetId, DeleteFileGroupJob(fileGroupId))

      expectMsg(JobCompleted(documentSetId))
    }

    "respond immediately when asked to cancel unknown job" in new JobQueueContext {

      fileGroupJobQueue ! CancelFileUpload(documentSetId, fileGroupId)

      expectMsg(JobCompleted(documentSetId))
    }
    
    
    "don't notify busy workers" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      
      submitJob(documentSetId)
      worker.expectATask
      
      submitJob(documentSetId + 1)
      worker.expectNoMsg(50 millis)
    }

    "don't send tasks to busy workers even if they ask for them" in new JobQueueContext {
      fileGroupJobQueue ! RegisterWorker(worker.ref)

      submitJob(documentSetId)
      worker.expectATask

      submitJob(documentSetId + 1)
      fileGroupJobQueue.tell(ReadyForTask, worker.ref)

      worker.expectNoMsg(50 millis)
    }

    "reschedule an in progress task if worker terminates" in new SingleTaskContext {
      val failingWorker = system.actorOf(Props[DummyTaskWorker])
      fileGroupJobQueue ! RegisterWorker(failingWorker)

      submitJob(documentSetId)
      
      fileGroupJobQueue ! RegisterWorker(worker.ref)
      
      system.stop(failingWorker)
      worker.expectMsg(TaskAvailable)
    }

    abstract class JobQueueContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val fileGroupId = 2l
      protected val numberOfUploadedFiles = 10
      protected val uploadedFileIds: Seq[Long] = Seq.tabulate(numberOfUploadedFiles)(_.toLong)
      protected val fileId = 20l
      
      protected var fileGroupJobQueue: ActorRef = _
      protected var worker: WorkerTestProbe = _
      protected var progressReporter: TestProbe = _
      protected var documentIdSupplier: TestProbe = _

      def before = {
        progressReporter = TestProbe()
        documentIdSupplier = TestProbe()
        
        fileGroupJobQueue = system.actorOf(TestFileGroupJobQueue(uploadedFileIds, progressReporter.ref, documentIdSupplier.ref))
        worker = createNWorkers(1).head
      }

      protected def createNWorkers(numberOfWorkers: Int): Seq[WorkerTestProbe] =
        Seq.fill(numberOfWorkers)(new WorkerTestProbe(documentSetId, fileGroupId, system))

      protected def submitJob(documentSetId: Long = documentSetId) =
        fileGroupJobQueue ! SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, UploadProcessOptions("en", false)))

      protected def expectTasks(workers: Seq[WorkerTestProbe]) = workers.map { _.expectATask }
      protected def expectCancellation(workers: Seq[WorkerTestProbe]) = workers.map { _.expectMsg(CancelTask) }

      protected def mustMatchUploadedFileIds(tasks: Seq[CreatePagesTask], uploadedFileIds: Seq[Long]) =
        tasks.map(_.uploadedFileId) must containTheSameElementsAs(uploadedFileIds)
    }

    abstract class SingleTaskContext extends JobQueueContext {
      override protected val uploadedFileIds: Seq[Long] = Seq(1)  
    }
    
    class WorkerTestProbe(documentSetId: Long, fileGroupId: Long, actorSystem: ActorSystem) extends TestProbe(actorSystem) {

      def expectATask = {
        expectMsg(TaskAvailable)
        reply(ReadyForTask)

        expectMsgClass(classOf[CreatePagesTask])
      }

      def expectDeleteFileUploadJob = {
        expectMsg(TaskAvailable)
        reply(ReadyForTask)

        expectMsg(DeleteFileUploadJob(documentSetId, fileGroupId))
      }

      def expectNoTaskAvailable(jobQueue: ActorRef) = {
        jobQueue ! ReadyForTask
        expectNoMsg(50 millis)
      }

      def completeCancelledTask(uploadedFileId: Long) = {
        expectMsg(CancelTask)
        reply(TaskDone(documentSetId, None))
      }

    }

    class ImmediateJobCompleter(worker: ActorRef, outputFileId: Long) extends TestActor.AutoPilot {
      def run(sender: ActorRef, message: Any): TestActor.AutoPilot = {
        message match {
          case TaskAvailable => sender.tell(ReadyForTask, worker)
          case CreatePagesTask(ds, fg, uf) => {
            sender.tell(TaskDone(ds, Some(outputFileId)), worker)
            sender.tell(ReadyForTask, worker)
          }
          case DeleteFileUploadJob(ds, fg) => {
            sender.tell(TaskDone(ds, None), worker)
            sender.tell(ReadyForTask, worker)
          }
          case CreateDocumentsTask(ds, fg, split) => {
            sender.tell(TaskDone(ds, None), worker)
            sender.tell(ReadyForTask, worker)
          }
        }
        TestActor.KeepRunning
      }
    }

    object ActAsImmediateJobCompleter {
      def apply(probe: TestProbe, outputFileId: Long): TestProbe = {
        probe.setAutoPilot(new ImmediateJobCompleter(probe.ref, outputFileId))
        probe
      }
    }
  }
}

class DummyTaskWorker extends Actor {

  def receive = {
    case TaskAvailable => sender ! ReadyForTask
  }

}