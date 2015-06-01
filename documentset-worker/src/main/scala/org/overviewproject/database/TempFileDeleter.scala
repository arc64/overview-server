package org.overviewproject.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.StaticQuery.interpolation
import org.overviewproject.models.tables.TempDocumentSetFiles
import org.overviewproject.database.Slick.simple._


/**
 * Deletes [[File]]s referenced by [[TempDocumentSetFile]] entries.
 * If upload processing is interrupted before a [[File]] has been associated with a [[Document]],
 * then the [[File]] is only referred to by a [[TempDocumentSetFile]].  [[TempFileDeleter]] is used
 * to cleanup a cancelled or interrupted upload processing job.
 */
trait TempFileDeleter extends SlickClient {

  def delete(documentSetId: Long): Future[Unit] = db { implicit session =>
    releaseFiles(documentSetId)
    deleteTempDocumentSets(documentSetId)
  }

  private def releaseFiles(documentSetId: Long)(implicit session: Session): Unit = {
    val fileReferences = sqlu"""
      WITH ids AS (
        SELECT id
        FROM file
        WHERE id IN (SELECT file_id FROM temp_document_set_file WHERE document_set_id = $documentSetId)
        FOR UPDATE
      )
      UPDATE file
      SET reference_count = reference_count - 1
      WHERE id IN (SELECT id FROM ids)
      AND reference_count > 0
    """

    fileReferences.execute
  }
  
  private def deleteTempDocumentSets(documentSetId: Long)(implicit session: Session): Unit = {
    val tempDocumentSetFiles = TempDocumentSetFiles.filter(_.documentSetId === documentSetId)

    tempDocumentSetFiles.delete
  }

}

object TempFileDeleter {
  def apply(): TempFileDeleter = new TempFileDeleter with SlickSessionProvider
}
