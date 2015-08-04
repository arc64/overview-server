package com.overviewdocs.tree.orm

import com.overviewdocs.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class TempDocumentSetFile (
  documentSetId: Long,
  fileId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] with DocumentSetComponent {
	override def id = compositeKey(documentSetId, fileId)

}