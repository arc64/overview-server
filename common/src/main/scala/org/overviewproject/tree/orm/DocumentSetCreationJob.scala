package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.dsl.ManyToOne
import org.squeryl.KeyedEntity
import scala.runtime.ScalaRunTime

import org.overviewproject.postgres.PostgresqlEnum
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.models.{DocumentSetCreationJob => GoodDocumentSetCreationJob}
import org.overviewproject.models.{DocumentSetCreationJobType => GoodDocumentSetCreationJobType}
import org.overviewproject.models.{DocumentSetCreationJobState => GoodDocumentSetCreationJobState}
import org.overviewproject.tree.DocumentSetCreationJobType

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value

  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
  val FilesUploaded = Value(4, "FILES_UPLOADED")
  val TextExtractionInProgress = Value(5, "TEXT_EXTRACTION_IN_PROGRESS")
}

import DocumentSetCreationJobState._

case class DocumentSetCreationJob(
  id: Long = 0L,
  documentSetId: Long = 0L,
  @Column("type") jobType: DocumentSetCreationJobType.Value,
  lang: String = "en",
  suppliedStopWords: String = "",
  importantWords: String = "",
  documentcloudUsername: Option[String] = None,
  documentcloudPassword: Option[String] = None,
  splitDocuments: Boolean = false,
  contentsOid: Option[Long] = None,
  sourceDocumentSetId: Option[Long] = None,
  fileGroupId: Option[Long] = None,
  treeTitle: Option[String] = None,
  tagId: Option[Long] = None,
  state: DocumentSetCreationJobState.Value,
  fractionComplete: Double = 0.0,
  statusDescription: String = "",
  treeDescription: Option[String] = None,
  retryAttempts: Int = 0,
  canBeCancelled: Boolean = true
  ) extends KeyedEntity[Long] with DocumentSetComponent {

  def this() = this(jobType = DocumentSetCreationJobType.DocumentCloud, state = NotStarted)

  /** Override Squeryl's silly equals() method */
  override def equals(other: Any) : Boolean = {
    ScalaRunTime._equals(this, other)
  }

  override def isPersisted(): Boolean = (id > 0)

  def toDocumentSetCreationJob: GoodDocumentSetCreationJob = GoodDocumentSetCreationJob(
    id,
    documentSetId,
    GoodDocumentSetCreationJobType(jobType.id),
    retryAttempts,
    lang,
    suppliedStopWords,
    importantWords,
    splitDocuments,
    documentcloudUsername,
    documentcloudPassword,
    contentsOid,
    fileGroupId,
    sourceDocumentSetId,
    treeTitle,
    treeDescription,
    tagId,
    GoodDocumentSetCreationJobState(state.id),
    fractionComplete,
    statusDescription,
    canBeCancelled
  )
}
