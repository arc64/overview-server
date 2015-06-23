package org.overviewproject.models

import java.util.Date // should be java.time.LocalDateTime
import play.api.libs.json.JsObject

import org.overviewproject.models.DocumentDisplayMethod.DocumentDisplayMethod

/** A DocumentHeader that's as lightweight as possible.
  *
  * The main difference between this and a full-fledged Document: its "text"
  * and "metadata" are _always_ empty. That makes DocumentInfo take ~100bytes,
  * while Document is often &gt;5kb.
  */
case class DocumentInfo(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String],
  override val createdAt: Date,
  override val displayMethod: Option[DocumentDisplayMethod],
  val hasFileView: Boolean
) extends DocumentHeader {
  override val metadataJson = JsObject(Seq())
  override val text = ""

  override def viewUrl: Option[String] = {
    url
      .orElse(if (hasFileView) Some(s"/documents/${id}.pdf") else None)
  }
}
