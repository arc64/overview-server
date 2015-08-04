package com.overviewdocs.upgrade.move_files

import play.api.libs.iteratee.Iteratee
import scala.concurrent.{Await,Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.{DB,DataSource,DatabaseConfiguration}
import com.overviewdocs.models.File
import com.overviewdocs.models.tables.Files
import com.overviewdocs.util.TempFile

/** Move File blobs to wherever it is configured to go in application.conf.
  *
  * (In other words, use application.conf and environment variables.)
  *
  * We move one file at a time, to keep things simple. Do <em>not</em> run
  * multiple instances of this program, or the two versions will interfere
  * and catastrophe will ensue.
  *
  * We assume nobody is writing to the old location prefix while we process.
  * The whole point of this script is to get rid of it, after all.
  */
object Main {
  private val BatchSize: Int = 1000

  def main(args: Array[String]): Unit = {
    connectToDatabase
    moveAllFiles
  }

  /** Globally connects to the database using settings in config. */
  def connectToDatabase: Unit = {
    val databaseConfig = DatabaseConfiguration.fromConfig
    val dataSource = DataSource(databaseConfig)
    DB.connect(dataSource)
  }

  /** Calls moveSomeFiles() a batch at a time. */
  private def moveAllFiles: Unit = {
    while (BatchSize == moveSomeFiles(BatchSize)) {
      // After a full batch, there may be more
      System.out.println("Yay, another batch of files!")
    }

    // After a half-batch (or 0), there are no more
    System.out.println("There are no more files to move.")
  }

  /** Finds some files that need moving and moves them one by one. */
  private def moveSomeFiles(limit: Int): Int = {
    val files = findSomeFiles(limit)
    System.out.println(s"Working on a batch of ${files.length} files...")
    files.foreach(moveFile _)
    files.length
  }

  /** Copies data to BlobStorage and then removes it from the database.
    *
    * We read the entire file into memory and then write it from memory. Why?
    * Because it's simple!
    */
  private def moveFile(file: File): Unit = {
    val contentsLocation: Future[String] = copyBlob(BlobBucketId.FileContents, file.contentsLocation, file.contentsSize)

    val viewLocation: Future[String] = if (file.contentsLocation == file.viewLocation) {
      contentsLocation
    } else {
      copyBlob(BlobBucketId.FileView, file.viewLocation, file.viewSize)
    }

    updateFile(file, await(contentsLocation), await(viewLocation))
  }

  /** Creates a new blob from the blob at the given location.
    *
    * This works by reading the entire blob into memory and then writing it.
    * Yup, a hack.
    *
    * @return The new location.
    */
  private def copyBlob(bucket: BlobBucketId, location1: String, nBytes: Long): Future[String] = {
    System.out.println(s"Copying blob at ${location1} (${nBytes} bytes) ...")

    val tempFile = new TempFile
    var realNBytes = 0L

    val enumerator = await(BlobStorage.get(location1))
    await(enumerator.run(Iteratee.foreach { bytes: Array[Byte] =>
      tempFile.outputStream.write(bytes)
      realNBytes += bytes.length
    }))
    tempFile.outputStream.close

    if (realNBytes != nBytes) {
      throw new Exception(s"Wrong nBytes. Read ${realNBytes}, expected ${nBytes}")
    }

    BlobStorage.create(bucket, tempFile.inputStream, nBytes)
  }

  /** Updates a File in the database to have the new location.
    *
    * Destroys Large Objects associated with the File.
    */
  private def updateFile(file: File, contentsLocation: String, viewLocation: String): Unit = {
    System.out.println(s"Updating file ${file.id} to have locations ${contentsLocation} and ${viewLocation} ...")

    DB.withConnection { connection =>
      val session = DB.slickSession(connection)

      import com.overviewdocs.database.Slick.simple._
      Files
        .filter(_.id === file.id)
        .map((f) => (f.contentsLocation, f.viewLocation))
        .update((contentsLocation, viewLocation))(session)

      await(BlobStorage.delete(file.contentsLocation))
      if (file.viewLocation != file.contentsLocation) {
        await(BlobStorage.delete(file.viewLocation))
      }
    }
  }

  /** Finds some Files that need copying. */
  private def findSomeFiles(limit: Int): Seq[File] = {
    System.out.println(s"Finding <= ${limit} files to transfer...")

    DB.withConnection { connection =>
      val session = DB.slickSession(connection)

      import com.overviewdocs.database.Slick.simple._
      Files
        .filter(!_.contentsLocation.startsWith("s3:"))
        .take(limit)
        .list(session)
    }
  }

  /** Makes things synchronous. Awesome. */
  private def await[A](future: Future[A]): A = {
    Await.result(future, Duration.Inf)
  }
}
