package com.overviewdocs.jobhandler.filegroup

import akka.actor._
import akka.testkit._

import com.overviewdocs.jobhandler.filegroup.ClusteringJobQueueProtocol._
import com.overviewdocs.jobhandler.filegroup.FileGroupJobQueueProtocol._
import com.overviewdocs.messages.ClusterCommands.CancelFileUpload
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.tree.DocumentSetCreationJobType._
import com.overviewdocs.tree.orm.DocumentSetCreationJob
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import org.specs2.mutable.{ Before, Specification }

class FileGroupJobManagerSpec extends Specification {
  sequential

  "FileGroupJobManager" should {

    "start text extraction job at text extraction job queue" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
    }

    "start clustering job when text extraction is complete" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectMsgType[SubmitJob]
      fileGroupJobQueue.reply(JobCompleted(documentSetId))

      clusteringJobQueue.expectMsg(ClusterDocumentSet(documentSetId))
    }

    "update job state when clustering request is received" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand

      updateJobStateWasCalled(documentSetId)
    }

    "restart in progress jobs and cancel cancelled jobs found during startup" in new RestartingFileGroupJobManagerContext {
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet1, CreateDocumentsJob(fileGroup1, options)))
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet2, CreateDocumentsJob(fileGroup2, options)))

      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet1, fileGroup3))
      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet2, fileGroup4))
    }

    "delete cancelled jobs found during startup" in new RestartingFileGroupJobManagerContext {
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet1, CreateDocumentsJob(fileGroup1, options)))
      fileGroupJobQueue.expectMsg(SubmitJob(interruptedDocumentSet2, CreateDocumentsJob(fileGroup2, options)))

      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet1, fileGroup3))
      fileGroupJobQueue.expectMsg(CancelFileUpload(cancelledDocumentSet2, fileGroup4))

      fileGroupJobManager ! JobCompleted(cancelledDocumentSet1)
      fileGroupJobQueue.expectMsg(SubmitJob(cancelledDocumentSet1, DeleteFileGroupJob(fileGroup3)))
    }

    "fail job if restart limit is reached" in new RestartLimitContext {
      failJobWasCalled(interruptedDocumentSet)
    }

    "increase retryAttempts when restarting jobs" in new RestartingFileGroupJobManagerContext {
      increaseRetryAttemptsWasCalled(interruptedDocumentSet1, fileGroup1)
      increaseRetryAttemptsWasCalled(interruptedDocumentSet2, fileGroup2)
    }

    "cancel text extraction when requested" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
      fileGroupJobQueue.expectMsg(CancelFileUpload(documentSetId, fileGroupId))
    }

    "don't start clustering cancelled job" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsgType[SubmitJob]
      fileGroupJobQueue.expectMsgType[CancelFileUpload]
      fileGroupJobQueue.reply(JobCompleted(documentSetId))

      clusteringJobQueue.expectNoMsg
    }

    "don't start text extraction if no job is found" in new NoJobContext {
      fileGroupJobManager ! clusterCommand

      fileGroupJobQueue.expectNoMsg
    }
    
    "try again if no job is found initially" in new DelayedJobContext {
      fileGroupJobManager ! clusterCommand
      
      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
    }

    "delete cancelled job after cancellation is complete" in new FileGroupJobManagerContext {
      fileGroupJobManager ! clusterCommand
      fileGroupJobManager ! cancelCommand

      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, CreateDocumentsJob(fileGroupId, options)))
      fileGroupJobQueue.expectMsg(CancelFileUpload(documentSetId, fileGroupId))
      fileGroupJobQueue.reply(JobCompleted(documentSetId))

      fileGroupJobQueue.expectMsg(SubmitJob(documentSetId, DeleteFileGroupJob(fileGroupId)))
    }

    abstract class FileGroupJobManagerContext extends ActorSystemContext with Before with JobParameters {

      var fileGroupJobManager: TestActorRef[TestFileGroupJobManager] = _
      var fileGroupJobQueue: TestProbe = _
      var clusteringJobQueue: TestProbe = _

      override def before = {
        fileGroupJobQueue = TestProbe()
        clusteringJobQueue = TestProbe()
        fileGroupJobManager = TestActorRef(
          new TestFileGroupJobManager(fileGroupJobQueue.ref, clusteringJobQueue.ref, uploadJob, interruptedJobs, cancelledJobs))

      }

      private def jm = fileGroupJobManager.underlyingActor
      
      protected def uploadJob: Option[DocumentSetCreationJob] = Some(
        DocumentSetCreationJob(documentSetId = documentSetId, jobType = FileUpload, fileGroupId = Some(fileGroupId),
          state = FilesUploaded))

      protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq.empty
      protected def cancelledJobs: Seq[(Long, Long)] = Seq.empty

      protected def updateJobStateWasCalled(documentSetId: Long) = jm.updateJobStateFn.wasCalledWith(documentSetId)
      

      protected def failJobWasCalled(documentSetId: Long) = {
        def matchDocumentSetId(job: DocumentSetCreationJob) = {
          job.documentSetId must be equalTo (documentSetId)
        }

        jm.failJobFn.wasCalledWithMatch(matchDocumentSetId _)
      }

      protected def increaseRetryAttemptsWasCalled(documentSetId: Long, fileGroupId: Long) = {
        def matchDsAndFg(job: DocumentSetCreationJob) = {
          job.documentSetId must be equalTo (documentSetId)
          job.fileGroupId must beSome(fileGroupId)
        }
        
        jm.increaseRetryAttemptFn.wasCalledWithMatch(matchDsAndFg)
      }
    }

    abstract class RestartingFileGroupJobManagerContext extends FileGroupJobManagerContext {
      val interruptedDocumentSet1: Long = 10l
      val interruptedDocumentSet2: Long = 20l
      val cancelledDocumentSet1: Long = 30l
      val cancelledDocumentSet2: Long = 40l

      val fileGroup1: Long = 15l
      val fileGroup2: Long = 25l
      val fileGroup3: Long = 35l
      val fileGroup4: Long = 45l

      override protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq(
        (interruptedDocumentSet1, fileGroup1, 0),
        (interruptedDocumentSet2, fileGroup2, 0))

      override protected def cancelledJobs: Seq[(Long, Long)] = Seq(
        (cancelledDocumentSet1, fileGroup3),
        (cancelledDocumentSet2, fileGroup4))
    }

    abstract class NoJobContext extends FileGroupJobManagerContext {
      override protected def uploadJob: Option[DocumentSetCreationJob] = None
    }
    
    abstract class DelayedJobContext extends FileGroupJobManagerContext {
      private var callCount = 0
      
      override protected def uploadJob: Option[DocumentSetCreationJob] = {
        callCount += 1
        
        if (callCount > 1) super.uploadJob
        else None
      }
    }

    abstract class RestartLimitContext extends FileGroupJobManagerContext {
      val interruptedDocumentSet: Long = 10l
      val fileGroup: Long = 15l

      override protected def interruptedJobs: Seq[(Long, Long, Int)] = Seq(
        (interruptedDocumentSet, fileGroup, 3))
    }
  }

}
