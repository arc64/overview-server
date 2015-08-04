package com.overviewdocs.database.orm.stores

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm.stores.BaseStore
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import com.overviewdocs.tree.DocumentSetCreationJobType._
import com.overviewdocs.database.orm.Schema.documentSetCreationJobs
import com.overviewdocs.tree.orm.DocumentSetCreationJob

object DocumentSetCreationJobStore extends BaseStore(documentSetCreationJobs) {

  def deleteNonRunningJobs(documentSetId: Long): Unit = {
    val deleteCsvUploadContent = from(documentSetCreationJobs)(dscj =>
      where(
        dscj.documentSetId === documentSetId and
          dscj.jobType === CsvUpload and
          dscj.state <> InProgress)
        select (&(lo_unlink(dscj.contentsOid)))).toIterable

    val jobsToDelete = from(documentSetCreationJobs)(dscj =>
      where(
        dscj.documentSetId === documentSetId and
          dscj.state <> InProgress)
        select (dscj))

    documentSetCreationJobs.delete(jobsToDelete)
  }

  // Should only be called on a reclustering job
  def deleteByState(documentSetId: Long, jobState: DocumentSetCreationJobState): Unit = {
    val jobsToDelete = from(documentSetCreationJobs)(dscj =>
      where(dscj.documentSetId === documentSetId and
        dscj.state === jobState)
        select (dscj))

    documentSetCreationJobs.delete(jobsToDelete)
  }

  def deleteById(id: Long): Unit = {
    val jobToDelete = from(documentSetCreationJobs)(dscj =>
      where(dscj.id === id)
        select (dscj))
        
    documentSetCreationJobs.delete(jobToDelete)
  }
  
  def cancelByDocumentSet(documentSetId: Long): Iterable[DocumentSetCreationJob] = {
    val jobs = from(documentSetCreationJobs)(dscj =>
      where (dscj.documentSetId === documentSetId)
      select (dscj)
    ).forUpdate
    
    jobs.map { j => insertOrUpdate(j.copy(state = Cancelled))}
  }
}