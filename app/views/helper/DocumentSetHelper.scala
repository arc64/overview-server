package views.helper

import play.api.i18n.Messages

import com.overviewdocs.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState}

object DocumentSetHelper {
  /**
   * @param jobDescriptionKey A key, like "clustering_level:4"
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit messages: Messages): String = {
    val keyAndArgs : Seq[String] = jobDescriptionKey.split(':')
    val key = keyAndArgs.head
    if (key == "") {
      ""
    } else {
      val m = views.ScopedMessages("views.ImportJob._documentSetCreationJob.job_state_description")
      m(keyAndArgs.head, keyAndArgs.drop(1) : _*)
    }
  }

  /**
   * @param job A DocumentSetCreationJob
   * @param nAheadInQueue Number of jobs ahead of this one in the queue
   * @return A translated string, like "Clustering (4)"
   */
  def jobDescriptionMessage(job: DocumentSetCreationJob, nAheadInQueue: Long)(implicit messages: Messages): String = {
    if (
        (job.state == DocumentSetCreationJobState.NotStarted && nAheadInQueue > 0) ||
        (job.state == DocumentSetCreationJobState.FilesUploaded && job.statusDescription == "file_processing_not_started")) {
      views.Magic.t("views.ImportJob._documentSetCreationJob.jobs_to_process", nAheadInQueue)
    } else {
      jobDescriptionKeyToMessage(job.statusDescription)
    }
  }
}
