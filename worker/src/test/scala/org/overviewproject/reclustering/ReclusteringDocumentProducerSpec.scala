package org.overviewproject.reclustering

import org.overviewproject.models.Document
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress.ProgressAbortFn
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.overviewproject.util.Progress.Progress
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Retrieving

class ReclusteringDocumentProducerSpec extends Specification with Mockito {

  "ReclusteringDocumentProducer" should {

    class TestReclusteringDocumentProducer(
      override protected val pagedDocumentFinder: PagedDocumentFinder,
      override protected val consumer: DocumentConsumer,
      override protected val progAbort: ProgressAbortFn) extends ReclusteringDocumentProducer

    trait ReclusteringContext extends Before {
      val documentSetId = 1l
      val consumer = smartMock[DocumentConsumer]
      val progAbort = mock[ProgressAbortFn] // smartMock triggers bug https://code.google.com/p/mockito/issues/detail?id=107
      val numberOfDocuments = 5
      val factory = org.overviewproject.test.factories.PodoFactory

      val documentFinder = smartMock[PagedDocumentFinder]
      documentFinder.numberOfDocuments returns numberOfDocuments
      documentFinder.findDocuments(1) returns Seq.tabulate(numberOfDocuments) { n =>
        // Gotta give id=n+1, because id=0 will auto-assign an ID
        factory.document(id=n+1, text=s"text-$n")
      }
      documentFinder.findDocuments(2) returns Seq.empty

      val documentProducer = new TestReclusteringDocumentProducer(documentFinder, consumer, progAbort)

      override def before = setupProgAbort

      protected def setupProgAbort: Unit = progAbort.apply(any) returns false

      private def createMockDocuments: Seq[Document] = {
        Seq.tabulate(numberOfDocuments) { n => factory.document(id=n, text=s"text-$n") }
      }
    }

    trait CancelledClustering extends ReclusteringContext {

      override protected def setupProgAbort: Unit =
        progAbort.apply(any) returns false thenReturn true
    }

    "read documents by page" in new ReclusteringContext {
      val numberOfDocumentsProduced = documentProducer.produce

      numberOfDocumentsProduced must be equalTo (numberOfDocuments)
      there was one(documentFinder).findDocuments(1)
      there was one(documentFinder).findDocuments(2)
    }

    "pass documents to consumer" in new ReclusteringContext {
      documentProducer.produce

      for { n <- 0 until numberOfDocuments } yield {
        there was one(consumer).processDocument(n+1, s"text-$n")
      }
    }

    "report progress" in new ReclusteringContext {
      documentProducer.produce
      val status = Seq.tabulate(numberOfDocuments)(n =>
        Progress(0.5 * (1.0 + n) / numberOfDocuments, Retrieving(n + 1, numberOfDocuments)))

      there was
        one(progAbort).apply(status(0)) andThen
        one(progAbort).apply(status(1)) andThen
        one(progAbort).apply(status(2)) andThen
        one(progAbort).apply(status(3)) andThen
        one(progAbort).apply(status(4))

    }
    
    "tells consumer when all documents have been found" in new ReclusteringContext {
      documentProducer.produce
      
      there was one(consumer).productionComplete
    }

    "stop processing when cancelled" in new CancelledClustering {
      documentProducer.produce

      there was one(consumer).processDocument(1, "text-0") andThen
        one(consumer).processDocument(2, "text-1")
      for { n <- 2 until numberOfDocuments } yield {
        there was no(consumer).processDocument(n+1, s"text-$n")
      }
    }

  }
}
