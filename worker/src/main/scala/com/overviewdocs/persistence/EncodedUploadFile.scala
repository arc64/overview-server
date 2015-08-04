/*
 * UploadedFileLoader.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package com.overviewdocs.persistence

import java.sql.Connection

import com.overviewdocs.persistence.orm.Schema
import com.overviewdocs.tree.orm.UploadedFile

/** Information describing an uploaded file */
trait EncodedUploadFile {
  val contentType: String
  val size: Long

  private val ContentTypeEncoding = ".*charset=([^\\s]*)".r

  /**
   * @return a string with the value of the charset field in contentType,
   * or None, if the contentType could not be parsed.
   */
  def encoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
  }
}

/** Helper for loading an UploadedFile from the database */
object EncodedUploadFile {
  import com.overviewdocs.postgres.SquerylEntrypoint._

  /** @return the UploadedFile specified by the uploadedFileId */
  def load(uploadedFileId: Long)(implicit c: Connection): EncodedUploadFile = {
    val upload = Schema.uploadedFiles.lookup(uploadedFileId).get

    new EncodedUploadFileImpl(upload)
  }

  private class EncodedUploadFileImpl(uploadedFile: UploadedFile) extends EncodedUploadFile {
    val contentType: String = uploadedFile.contentType
    val size: Long = uploadedFile.size
  }
}
