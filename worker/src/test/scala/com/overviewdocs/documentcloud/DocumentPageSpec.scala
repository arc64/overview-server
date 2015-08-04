package com.overviewdocs.documentcloud

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class DocumentPageSpec extends Specification {

  "DocumentPage" should {
    
    
    trait DocumentPageContext extends Scope {
      val id = "documentCloudId"
      val title = "title"
      val pageUrlPrefix = "pageUrlTemplate-p"
      val pageNum = 5
      val document = Document(id, title, pageNum, "access", "textUrl", s"$pageUrlPrefix{page}")
      val documentPage = new DocumentPage(document, pageNum)
    }
    
    
    "return the document page url for DocumentPage" in new DocumentPageContext {
      documentPage.url must be equalTo(s"$pageUrlPrefix$pageNum")
    }
    
    "return id with page number" in new DocumentPageContext {
      documentPage.id must be equalTo(s"$id#p$pageNum")
    }
    
    "return title unchanged" in new DocumentPageContext {
      documentPage.title must be equalTo(title)
    }
    
    
    
  }
}
