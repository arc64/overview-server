package org.overviewproject.jobhandler.filegroup.task

import akka.actor.Props
import org.overviewproject.test.ParameterStore
import akka.actor.ActorRef
import scala.concurrent.Future
import scala.concurrent.Promise
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcessCreator
import org.overviewproject.models.GroupedFileUpload

class TestFileGroupTaskWorker(jobQueuePath: String,
                              progressReporterPath: String,
                              fileRemovalQueuePath: String,
                              fileGroupRemovalQueuePath: String, 
                              processSelector: UploadProcessSelector,
                              uploadedFile: Option[GroupedFileUpload],
                              outputFileId: Long) extends FileGroupTaskWorker  {

  val executeFn = ParameterStore[Unit]
  val deleteFileUploadJobFn = ParameterStore[(Long, Long)]
  val deleteFileUploadPromise = Promise[Unit]

  private case class StepInSequence(n: Int, finalStep: FileGroupTaskStep) extends FileGroupTaskStep {
    def execute: FileGroupTaskStep = {
      executeFn.store(())
      if (n > 0) StepInSequence(n - 1, finalStep)
      else finalStep
    }
  }

  override protected val jobQueueSelection = context.actorSelection(jobQueuePath)
  override protected val progressReporterSelection = context.actorSelection(progressReporterPath)
  override protected val fileRemovalQueue = context.actorSelection(fileRemovalQueuePath)
  override protected val fileGroupRemovalQueue = context.actorSelection(fileGroupRemovalQueuePath)
  
  override protected val uploadedFileProcessSelector = processSelector
  
  override protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep =
    StepInSequence(1, CreatePagesProcessComplete(documentSetId, uploadedFileId, Some(outputFileId)))

  override protected def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean,
                                                  progressReporter: ActorRef): FileGroupTaskStep =
    StepInSequence(1, CreateDocumentsProcessComplete(documentSetId))

  override protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): FileGroupTaskStep =
    StepInSequence(1, DeleteFileUploadComplete(documentSetId, fileGroupId))

    override protected def findUploadedFile(uploadedFileId: Long) = Future.successful(uploadedFile)
}

object TestFileGroupTaskWorker {
  def apply(
    jobQueuePath: String, 
    progressReporterPath: String, 
    fileRemovalQueuePath: String, 
    fileGroupRemovalQueuePath: String, 
    uploadedFileProcessSelector: UploadProcessSelector,
    uploadedFile: Option[GroupedFileUpload],
    outputFileId: Long): Props =
    Props(new TestFileGroupTaskWorker(
        jobQueuePath, 
        progressReporterPath, 
        fileRemovalQueuePath, 
        fileGroupRemovalQueuePath, 
        uploadedFileProcessSelector, uploadedFile, outputFileId))
}
