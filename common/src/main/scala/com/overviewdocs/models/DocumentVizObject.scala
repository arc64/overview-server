package com.overviewdocs.models

import play.api.libs.json.JsObject

/** Joins a Document to a StoreObject.
  *
  * You may store data in the `json` column. We expect that to be infrequent,
  * though -- which is good, because there are lots and lots of these objects.
  */
case class DocumentStoreObject(
  documentId: Long,
  storeObjectId: Long,
  json: Option[JsObject]
)
