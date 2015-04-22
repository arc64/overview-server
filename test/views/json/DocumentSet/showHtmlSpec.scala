package views.json.DocumentSet

import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.libs.json.Json.toJson
import play.api.test.{FakeApplication,FakeRequest}

import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.test.Specification
import models.User

class showHtmlSpec extends Specification with JsonMatchers {
  step(start(FakeApplication()))

  "DocumentSet view generated Json" should {
    trait DocumentSetContext extends Scope with Mockito {
      implicit val request = FakeRequest()
      val documentSet = DocumentSet()
      val user = User()

      lazy val documentSetJson = showHtml(user, documentSet, 1L).toString
    }

    "contain id and html" in new DocumentSetContext {
      documentSetJson must /("id" -> documentSet.id)
      documentSetJson must beMatching(""".*"html":"[^<]*<.*>[^>]*".*""")
      documentSetJson must not contain ("state")
    }
  }

  step(stop)
}
