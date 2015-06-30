package views.json.ImportJob

import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.RequestHeader

import org.overviewproject.models.{ DocumentSet, DocumentSetCreationJob }

object show {
  def apply(
    job: DocumentSetCreationJob,
    documentSet: DocumentSet,
    nAheadInQueue: Int
  )(implicit
    messages: Messages,
    request: RequestHeader
  ) : JsValue = {
    toJson(Map(
      "id" -> toJson(documentSet.id),
      "html" -> toJson(views.html.ImportJob._documentSetCreationJob(job, documentSet, nAheadInQueue).toString)
    ))
  }
}
