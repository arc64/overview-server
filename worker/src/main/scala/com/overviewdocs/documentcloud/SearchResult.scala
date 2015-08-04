package com.overviewdocs.documentcloud

import play.api.libs.functional.syntax._
import play.api.libs.json._

/** Information from a DocumentCloud Search Result */
case class SearchResult(total: Int, page: Int, documents: Seq[Document])

/** Convert the JSON received when doing a DocumentCloud query to the above classes */
object ConvertSearchResult {

  implicit private val documentReads = (
    (__ \ "id").read[String] and
    (__ \ "title").read[String] and
    (__ \ "pages").read[Int] and
    (__ \ "access").read[String] and
    (__ \ "resources" \ "text").read[String] and
    (__ \ "resources" \ "page" \ "text").read[String])(Document)

  implicit private val searchResultReads = Json.reads[SearchResult]

  def apply(jsonSearchResult: String): SearchResult = Json.parse(jsonSearchResult).as[SearchResult]
}
