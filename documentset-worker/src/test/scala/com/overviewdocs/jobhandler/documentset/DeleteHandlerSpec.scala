package com.overviewdocs.jobhandler.documentset

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.specs2.mock.Mockito
import org.specs2.mutable.{ Before, Specification }
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._
import scala.concurrent.Promise

import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol._
import com.overviewdocs.database.DocumentSetCreationJobDeleter
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.documentset.DeleteHandlerProtocol._
import com.overviewdocs.jobhandler.JobProtocol._
import com.overviewdocs.searchindex.IndexClient
import com.overviewdocs.test.{ ActorSystemContext, ForwardingActor }

class DeleteHandlerSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteHandler" should {

    trait MockComponents {
      val searchIndexClient = smartMock[IndexClient]
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val jobDeleter = smartMock[DocumentSetCreationJobDeleter]
      val jobStatusChecker = smartMock[JobStatusChecker]

      val searchIndexRemoveDocumentSetPromise = Promise[Unit]
      val deleteDocumentSetPromise = Promise[Unit]
      val deleteJobPromise = Promise[Unit]

      searchIndexClient.removeDocumentSet(anyLong) returns searchIndexRemoveDocumentSetPromise.future
      documentSetDeleter.delete(anyLong) returns deleteDocumentSetPromise.future
      jobDeleter.deleteByDocumentSet(anyLong) returns deleteJobPromise.future
      jobDeleter.delete(anyLong) returns deleteJobPromise.future
    }

    class TestDeleteHandler(val fileRemovalQueuePath: String) extends DeleteHandler with MockComponents {
      override protected val JobWaitDelay = 5 milliseconds
      override protected val MaxRetryAttempts = 3
    }

    abstract class DeleteContext extends ActorSystemContext with Before {
      protected val documentSetId = 2l

      protected var parentProbe: TestProbe = _
      protected var deleteHandler: TestActorRef[TestDeleteHandler] = _
      protected var jobStatusChecker: JobStatusChecker = _
      
      protected var fileRemovalQueueProbe: TestProbe = _
      protected var fileRemovalQueue: ActorRef = _
      
      protected def da = deleteHandler.underlyingActor

      protected def searchIndexClient = da.searchIndexClient
      protected def searchIndexRemoveDocumentSetResult = da.searchIndexRemoveDocumentSetPromise
      protected def deleteDocumentSetResult = da.deleteDocumentSetPromise
      protected def deleteJobResult = da.deleteJobPromise

      protected def documentSetDeleter = da.documentSetDeleter
      protected def jobDeleter = da.jobDeleter

      protected def setJobStatus: Unit =
        da.jobStatusChecker isJobRunning (documentSetId) returns false

      override def before = {
        parentProbe = TestProbe()
        fileRemovalQueueProbe = TestProbe()
        fileRemovalQueue = system.actorOf(ForwardingActor(fileRemovalQueueProbe.ref))
        val fileRemovalQueuePath = fileRemovalQueue.path.toString
        
        deleteHandler = TestActorRef(Props(new TestDeleteHandler(fileRemovalQueuePath)), parentProbe.ref, "DeleteHandler")
        jobStatusChecker = da.jobStatusChecker

        setJobStatus
      }
    }

    abstract class DeleteWhileJobIsRunning extends DeleteContext {
      override protected def setJobStatus: Unit =
        da.jobStatusChecker isJobRunning (documentSetId) returns true thenReturns false
    }

    abstract class TimeoutWhileWaitingForJob extends DeleteContext {
      override protected def setJobStatus: Unit =
        jobStatusChecker isJobRunning (documentSetId) returns true

      override def before = {
        super.before
        parentProbe.watch(deleteHandler)
      }
    }

    "delete documents and alias with the specified documentSetId from the index" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      
      there was one(searchIndexClient).removeDocumentSet(documentSetId)
    }

    "delete document set related data" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      deleteJobResult.success(Unit)
      
      there was one(documentSetDeleter).delete(documentSetId)
    }

    "notify parent when deletion of documents and alias completes successfully" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)

      parentProbe.expectNoMsg(10 millis)

      searchIndexRemoveDocumentSetResult.success(Unit)
      parentProbe.expectNoMsg(10 millis)

      deleteDocumentSetResult.success(Unit)
      deleteJobResult.success(Unit)

      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "notify parent when deletion fails" in new DeleteContext {
      val error = new Exception("foo")

      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      
      searchIndexRemoveDocumentSetResult.failure(error)
      deleteDocumentSetResult.success(Unit)
      deleteJobResult.success(Unit)

      // FIXME: We can't distinguish between failure and success right now
      parentProbe.expectMsg(JobDone(documentSetId))
    }
    
    "tell file removal queue to scan for deleted files" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)
      
      searchIndexRemoveDocumentSetResult.success(())
      deleteDocumentSetResult.success(())
      deleteJobResult.success(())
      
      fileRemovalQueueProbe.expectMsg(RemoveFiles)
    }

    "wait until clustering job is no longer running before deleting" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, true)

      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      deleteJobResult.success(Unit)
      
      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "don't wait until clustering job is no longer running if waitForJobRemoval is false and no job is running" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)

      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      deleteJobResult.success(Unit)

      parentProbe.expectMsg(JobDone(documentSetId))
      there was no(jobStatusChecker).isJobRunning(documentSetId)
    }

    "delete any clustering job before deleting a document set if waitForJobRemoval is false" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId, false)

      there was one(jobDeleter).deleteByDocumentSet(documentSetId)

      searchIndexRemoveDocumentSetResult.success(Unit)
      deleteDocumentSetResult.success(Unit)
      deleteJobResult.success(Unit)

      parentProbe.expectMsg(JobDone(documentSetId))

    }

    "timeout while waiting for job that never gets cancelled" in new TimeoutWhileWaitingForJob {
      deleteHandler ! DeleteDocumentSet(documentSetId, true)

      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectTerminated(deleteHandler)
    }

    "delete reclustering job" in new DeleteContext {
      val jobId = 12L

      deleteHandler ! DeleteReclusteringJob(jobId)
      there was one(jobDeleter).delete(jobId)
      
      deleteJobResult.success(Unit)
      parentProbe.expectMsg(JobDone(jobId))
    }
  }
}

