package org.overviewproject.jobhandler.filegroup

import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.test.ParameterStore
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.filegroup.JobDescription._

class ProgressReporterSpec extends Specification {

  "ProgressReporter" should {

    "initialize progress when start received" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks, jobDescription)

      lastProgressStatusMustBe(documentSetId, 0.0, s"processing_files:0:$numberOfTasks")
    }

    "update count on task start" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks, jobDescription)
      progressReporter ! StartTask(documentSetId)

      lastProgressStatusMustBe(documentSetId, 0.0, s"processing_files:1:$numberOfTasks")
    }

    "update fraction complete on task done" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks, jobDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)

      lastProgressStatusMustBe(documentSetId, progressFraction * 1.0 / numberOfTasks, s"processing_files:1:$numberOfTasks")
    }

    "ignore updates after job is complete" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks, jobDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)

      progressReporter ! CompleteJob(documentSetId)
      progressReporter ! StartTask(documentSetId)

      lastProgressStatusMustBe(documentSetId, progressFraction * 1.0 / numberOfTasks, s"processing_files:1:$numberOfTasks")
    }

    "apply progress to started job step only" in new ProgressReporterContext {
      val numberOfJobSteps = 2
      val step1Fraction = 0.5

      progressReporter ! StartJob(documentSetId, numberOfJobSteps, jobDescription)
      progressReporter ! StartJobStep(documentSetId, numberOfTasks, step1Fraction, jobDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)

      lastProgressStatusMustBe(documentSetId, step1Fraction * 1.0 / numberOfTasks, s"processing_files:1:$numberOfTasks")
    }

    "include previously completed job step in progress fraction" in new ProgressReporterContext {
      val numberOfJobSteps = 2
      val step1Fraction = 0.3
      val step2Fraction = 0.7

      progressReporter ! StartJob(documentSetId, numberOfJobSteps, jobDescription)
      progressReporter ! StartJobStep(documentSetId, 1, step1Fraction, jobDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)
      progressReporter ! CompleteJobStep(documentSetId)

      lastProgressStatusMustBe(documentSetId, step1Fraction, s"processing_files:1:$numberOfJobSteps")
      
      progressReporter ! StartJobStep(documentSetId, numberOfTasks, step2Fraction, jobDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)

      lastProgressStatusMustBe(documentSetId, step1Fraction + step2Fraction * 1.0 / numberOfTasks,
        s"processing_files:1:$numberOfTasks")
    }
    
    "update description" in new ProgressReporterContext {
      val numberOfJobSteps = 1
      progressReporter ! StartJob(documentSetId, numberOfJobSteps, jobDescription)
      
      lastProgressStatusMustBe(documentSetId, 0.0, s"$jobDescription:0:$numberOfJobSteps")
      progressReporter ! StartJobStep(documentSetId, numberOfTasks, 1.0, jobStepDescription)
      progressReporter ! StartTask(documentSetId)
      progressReporter ! CompleteTask(documentSetId)
      
      lastProgressStatusMustBe(documentSetId, 0.2, s"$jobStepDescription:1:$numberOfTasks")
    }

    trait ProgressReporterContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val numberOfTasks = 5
      protected val uploadedFileId = 10l
      protected val progressFraction = 1.00
      protected val jobDescription = ExtractText
      protected val jobStepDescription = CreateDocument 
      
      protected var progressReporter: TestActorRef[TestProgressReporter] = _

      protected def lastProgressStatusMustBe(documentSetId: Long, fraction: Double, description: String) = {
        def matchParameters(p: (Long, Double, String)) = {
          p._1 must be equalTo (documentSetId)
          p._2 must beCloseTo(fraction, 0.01)
          p._3 must be equalTo (description)
        }

        progressReporter.underlyingActor.updateProgressFn.wasLastCalledWithMatch(matchParameters)
      }

      protected def updateJobStateWasCalled(documentSetId: Long, state: DocumentSetCreationJobState) =
        progressReporter.underlyingActor.updateJobStateFn.wasCalledWith((documentSetId, state))

      override def before = {
        progressReporter = TestActorRef(new TestProgressReporter)
      }
    }
  }

}

class TestProgressReporter extends ProgressReporter {

  val updateProgressFn = ParameterStore[(Long, Double, String)]
  val updateJobStateFn = ParameterStore[(Long, DocumentSetCreationJobState)]

  override protected val storage = new MockStorage

  class MockStorage extends Storage {
    def updateProgress(documentSetId: Long, fraction: Double, description: String): Unit =
      updateProgressFn.store((documentSetId, fraction, description))

    def updateJobState(documentSetId: Long, state: DocumentSetCreationJobState): Unit =
      updateJobStateFn.store((documentSetId, state))
  }
}