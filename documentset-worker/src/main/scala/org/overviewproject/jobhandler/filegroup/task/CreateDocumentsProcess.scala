package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import org.overviewproject.models.Document
import org.overviewproject.tree.orm.File
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.searchindex.ElasticSearchIndexClient
import scala.concurrent.Future

/**
 * Creates [[Document]]s from [[File]]s. [[File]]s are read from the database in chunks, with each step
 * in the process converting one page of the query result.
 * Depending on the value of the `splitDocuments` parameter, one [[Document]] is created per file, or
 * one [[Document]] is created for each [[Page]] of a [[File]].
 *
 * Only one [[CreateDocumentsProcess]] can be running in order to guarantee unique [[Document]] ids.
 *
 */
trait CreateDocumentsProcess {

  protected val ElasticSearchRequestTimeout = 5 minutes

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator

  /** Create the first step in the process */
  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean,
                               progressReporter: ActorRef): FileGroupTaskStep = {

    await(searchIndex.addDocumentSet(documentSetId))

    if (!splitDocuments) CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId),
      progressReporter)
    else CreateDocumentsFromPagesQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId),
      progressReporter)

  }

  private def await[T](f: Future[T]): T = Await.result(f, ElasticSearchRequestTimeout)

  // Parent class for steps in the process of creating documents.
  // Get one page of the query result reading Files, create Documents and add them to the SearchIndex
  // If there are no more results, update the document count, and delete the TempDocumentSetFiles entries
  // used to find the files.
  private abstract class CreateAndIndexDocument(documentSetId: Long, queryPage: Int,
                                                documentIdGenerator: DocumentIdGenerator,
                                                progressReporter: ActorRef) extends FileGroupTaskStep {
    protected val IndexingTimeout = 3 minutes

    override def execute: FileGroupTaskStep = {
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        files.foreach(reportStartTask)

        val documents = createDocumentsFromFiles(files)
        createDocumentsProcessStorage.writeDocuments(documents)

        indexDocuments(documents)

        files.foreach(reportCompleteTask)

        nextStep
        //
      } else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
        createDocumentsProcessStorage.refreshSortedDocumentIds(documentSetId)
        createDocumentsProcessStorage.deleteTempFiles(documentSetId)

        waitForIndexCompletion

        CreateDocumentsProcessComplete(documentSetId)
      }
    }

    private def indexDocuments(documents: Iterable[Document]): Unit = await(searchIndex.addDocuments(documents))
    private def waitForIndexCompletion: Unit = await(searchIndex.refresh)

    // Document creation is handled by subclasses, depending on value of splitDocuments
    protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document]
    protected def nextStep: FileGroupTaskStep

    private def reportStartTask(file: File): Unit =
      progressReporter ! StartTask(documentSetId)

    private def reportCompleteTask(file: File): Unit =
      progressReporter ! CompleteTask(documentSetId)
  }

  // Create one Document per File by concatenating the text of all Pages
  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int,
                                                      documentIdGenerator: DocumentIdGenerator,
                                                      progressReporter: ActorRef)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, progressReporter) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] = {
      files.map(createDocument(documentSetId, _))
    }

    override protected def nextStep: FileGroupTaskStep = {
      CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator, progressReporter)
    }

    private def createDocument(documentSetId: Long, file: File) = {
      val pages = createDocumentsProcessStorage.findFilePageText(file.id)

      val text = pages.foldLeft("")((text, page) => text + page.text.getOrElse(""))

      Document(
        documentIdGenerator.nextId,
        documentSetId,
        None,
        "",
        file.name,
        None,
        Seq(),
        new java.util.Date(),
        Some(file.id),
        None,
        text
      )
    }
  }

  // Create one Document for each Page in a File.
  private case class CreateDocumentsFromPagesQueryPage(documentSetId: Long, queryPage: Int,
                                                       documentIdGenerator: DocumentIdGenerator,
                                                       progressReporter: ActorRef)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, progressReporter) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] = {
      files.flatMap(createDocumentsFromPages(documentSetId, _))
    }

    override protected def nextStep: FileGroupTaskStep = {
      CreateDocumentsFromPagesQueryPage(documentSetId, queryPage + 1, documentIdGenerator, progressReporter)
    }

    private def createDocumentsFromPages(documentSetId: Long, file: File): Iterable[Document] = {
      val pages = createDocumentsProcessStorage.findFilePageText(file.id)

      pages.map { page => Document(
        documentIdGenerator.nextId,
        documentSetId,
        None,
        "",
        file.name,
        Some(page.number),
        Seq(),
        new java.util.Date(),
        Some(file.id),
        Some(page.id),
        page.text.getOrElse("")
      )}
    }
  }

  protected val searchIndex: ElasticSearchIndexClient
  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageText(fileId: Long): Iterable[PageText]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
    def refreshSortedDocumentIds(documentSetId: Long): Unit
    def deleteTempFiles(documentSetId: Long): Unit
  }
}
