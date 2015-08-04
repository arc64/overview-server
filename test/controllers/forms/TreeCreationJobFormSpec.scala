package controllers.forms

import play.api.data.FormError

import com.overviewdocs.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}
import com.overviewdocs.tree.DocumentSetCreationJobType

class TreeCreationJobFormSpec extends test.helpers.FormSpecification {
  trait JobApplyScope extends ApplyScope[DocumentSetCreationJob] {
    val documentSetId = 4L // random
    override def form = TreeCreationJobForm(documentSetId)
  }

  trait ValidScope extends JobApplyScope {
    val validTreeTitle = "title"
    val validTagId = None
    val requiredQuery = ""
    val validLang = "en"
    val requiredUsername = None
    val requiredPassword = None
    val requiredSplitDocuments = None
    val validSuppliedStopWords = ""
    val validImportantWords = ""

    override def args = Map(
      "tree_title" -> validTreeTitle,
      "tag_id" -> validTagId.map(_.toString).getOrElse(""),
      "lang" -> validLang,
      "supplied_stop_words" -> validSuppliedStopWords,
      "important_words" -> validImportantWords
    )

    def expectedValue = DocumentSetCreationJob(
      documentSetId = documentSetId,
      jobType = DocumentSetCreationJobType.Recluster,
      treeTitle = Some(validTreeTitle),
      tagId = None,
      lang = validLang,
      suppliedStopWords = validSuppliedStopWords,
      importantWords = validImportantWords,
      state = DocumentSetCreationJobState.NotStarted
    )
  }

  "TreeCreationJobForm" should {
    "create a correct DocumentSetCreationJob" in new ValidScope {
      value must beSome(expectedValue)
    }

    "disallow if lang is missing" in new ValidScope {
      override def args = super.args - "lang"
      error("lang") must beSome(FormError("lang", "error.required", Seq()))
    }

    "disallow if lang is empty" in new ValidScope {
      override def args = super.args + ("lang" -> "")
      error("lang") must beSome(FormError("lang", "error.required", Seq()))
    }

    "disallow if lang is not a valid lang" in new ValidScope {
      override def args = super.args + ("lang" -> "invalid language")
      error("lang") must beSome(FormError("lang", "forms.validation.unsupportedLanguage", Seq("invalid language")))
    }

    "disallow if supplied_stop_words is missing" in new ValidScope {
      override def args = super.args - "supplied_stop_words"
      error("supplied_stop_words") must beSome(FormError("supplied_stop_words", "error.required", Seq()))
    }

    "disallow if important_words is missing" in new ValidScope {
      override def args = super.args - "important_words"
      error("important_words") must beSome(FormError("important_words", "error.required", Seq()))
    }

    "disallow if title is missing" in new ValidScope {
      override def args = super.args - "tree_title"
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "disallow if title is empty" in new ValidScope {
      override def args = super.args + ("tree_title" -> "")
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "trim the title" in new ValidScope {
      override def args = super.args + ("tree_title" -> " title ")
      value must beSome(expectedValue)
    }

    "disallow if title is just spaces" in new ValidScope {
      override def args = super.args + ("tree_title" -> "  ")
      error("tree_title") must beSome(FormError("tree_title", "error.required", Seq()))
    }

    "set tagId" in new ValidScope {
      override def args = super.args + ("tag_id" -> "1125899906842624")
      value must beSome(expectedValue.copy(tagId=Some(1125899906842624L)))
    }

    "not set tagId when it is empty" in new ValidScope {
      override def args = super.args + ("tag_id" -> "")
      value must beSome(expectedValue)
    }
  }
}
