package com.overviewdocs.clone

object TagCloner {

  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Map[Long, Long] = {
	import com.overviewdocs.persistence.orm.Schema
	import com.overviewdocs.postgres.SquerylEntrypoint._
	
    val sourceTags = Schema.tags.where(t => t.documentSetId === sourceDocumentSetId)
    val cloneIds = sourceTags.map { t =>
      val clone = t.copy(id = 0, documentSetId = cloneDocumentSetId)  
      Schema.tags.insert(clone)
      clone.id
	}
	
	sourceTags.map(_.id).zip(cloneIds).toMap
  }
}