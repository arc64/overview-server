package org.overviewproject.models.tables

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.DocumentTag

class DocumentTagsImpl(tag: Tag) extends Table[DocumentTag](tag, "document_tag") {
  def documentId = column[Long]("document_id")
  def tagId = column[Long]("tag_id")
  def pk = primaryKey("document_tag_pkey", (documentId, tagId))

  def * = (documentId, tagId) <> (DocumentTag.tupled, DocumentTag.unapply)
}

object DocumentTags extends TableQuery(new DocumentTagsImpl(_))
