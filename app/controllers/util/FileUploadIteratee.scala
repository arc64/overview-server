package controllers.util

import java.util.UUID
import org.postgresql.PGConnection
import play.api.http.HeaderNames.{ CONTENT_DISPOSITION, CONTENT_TYPE, CONTENT_RANGE }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{ Done, Input, Iteratee }
import play.api.mvc.{ RequestHeader, Result }
import play.api.mvc.Results.BadRequest

import com.overviewdocs.database.{HasBlockingDatabase,LargeObject}
import models.upload.OverviewUpload

/**
 * Manages the upload of a file. Responsible for making sure the OverviewUpload object
 * is in sync with the LargeObject where the file is stored.
 */
trait FileUploadIteratee {
  private def DefaultBufferSize: Int = 1024 * 1024

  /** package for information extracted from request header */
  private case class UploadRequest(contentDisposition: String, contentType: String, start: Long, contentLength: Long)

  /** extract useful information from request header */
  private object UploadRequest {
    def apply(header: RequestHeader): Option[UploadRequest] = {
      val headers = header.headers

      val contentDisposition = headers.get(CONTENT_DISPOSITION).getOrElse("")
      val contentType = headers.get(CONTENT_TYPE).getOrElse("")
      val range = """(\d+)-(\d+)/(\d+)""".r // start-end/length
      for {
        contentRange <- headers.get(CONTENT_RANGE)
        rangeMatch <- range.findFirstMatchIn(contentRange)
      } yield {
        val List(start, end, length) = rangeMatch.subgroups.take(3)
        UploadRequest(contentDisposition, contentType, start.toLong, length.toLong)
      }
    }
  }

  /**
   * Checks the validity of the requests and processes the upload.
   */
  def store(userId: Long, guid: UUID, requestHeader: RequestHeader, bufferSize: Int = DefaultBufferSize): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {

    val uploadRequest = UploadRequest(requestHeader).toRight(BadRequest)

    uploadRequest.fold(
      errorStatus => Done(Left(errorStatus), Input.Empty),
      request => handleUploadRequest(userId, guid, request, bufferSize))
  }

  /**
   * @return an Iteratee for processing an upload request specified by info
   * The Iteratee will continue to consume the uploaded data even if an
   * error is encountered, but will not ignore the data received after the
   * error occurs.
   */
  private def handleUploadRequest(userId: Long, guid: UUID, request: UploadRequest, bufferSize: Int): Iteratee[Array[Byte], Either[Result, OverviewUpload]] = {
    val initialUpload = findValidUploadRestart(userId, guid, request)
      .getOrElse(Right(createUpload(userId, guid, request.contentDisposition, request.contentType, request.contentLength)))

    var buffer = Array[Byte]()

    Iteratee.fold[Array[Byte], Either[Result, OverviewUpload]](initialUpload) { (upload, chunk) =>
      val validUpload = upload.right.flatMap(validUploadWithChunk(_, chunk).toRight(BadRequest))
      validUpload.right.flatMap { u =>
        if ((buffer.size + chunk.size) >= bufferSize) {
          val bufferedChunk = buffer ++ chunk
          buffer = Array[Byte]()
          Right(appendChunk(u, bufferedChunk))
        } else {
          buffer ++= chunk
          Right(u)
        }
      }
    } map { u =>
      if (buffer.size > 0) u.right.map(appendChunk(_, buffer))
      else u
    }
  }

  /**
   * If adding the chunk to the upload does not exceed the expected
   * size of the upload, @return Some(upload), None otherwise
   */
  private def validUploadWithChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] =
    Some(upload).filter(u => u.uploadedFile.size + chunk.size <= u.size)

  /**
   * If the upload exists, verify the validity of the restart.
   * @return None if upload does not exist, otherwise an Either containing
   * an error status if request is invalid or the valid OverviewUpload.
   * If start is 0, any previously uploaded data is truncated.
   */
  private def findValidUploadRestart(userId: Long, guid: UUID, info: UploadRequest): Option[Either[Result, OverviewUpload]] = {
    findUpload(userId, guid).map(u =>
      info.start match {
        case 0 => Right(truncateUpload(u))
        case n if n == u.uploadedFile.size => Right(u)
        case _ => {
          cancelUpload(u)
          Left(BadRequest)
        }
      })
  }

  // Find an existing upload attempt
  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]

  // create a new upload attempt
  def createUpload(userId: Long, guid: UUID, contentDisposition: String, contentType: String, contentLength: Long): OverviewUpload

  // process a chunk of file data. @return the current OverviewUpload status, or None on failure	  
  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): OverviewUpload

  // Truncate the upload, deleting all previously uploaded data
  // @return the truncated OverviewUpload status, or None on failure
  def truncateUpload(upload: OverviewUpload): OverviewUpload

  // Remove all data from previously started upload
  def cancelUpload(upload: OverviewUpload)
}

/** Implementation that writes to database */
object FileUploadIteratee extends FileUploadIteratee with HasBlockingDatabase {
  import database.api._

  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = OverviewUpload.find(userId, guid)

  def createUpload(userId: Long, guid: UUID, contentDisposition: String, contentType: String, contentLength: Long): OverviewUpload = {
    val loid = blockingDatabase.run(database.largeObjectManager.create.transactionally)
    OverviewUpload(userId, guid, contentDisposition, contentType, contentLength, loid).save
  }

  def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): OverviewUpload = {
    blockingDatabase.run((for {
      lo <- database.largeObjectManager.open(upload.contentsOid, LargeObject.Mode.ReadWrite)
      _ <- lo.seek(upload.uploadedFile.size, LargeObject.Seek.Set)
      _ <- lo.write(chunk)
    } yield ()).transactionally)
    upload.withUploadedBytes(upload.uploadedFile.size + chunk.size).save
  }

  def truncateUpload(upload: OverviewUpload): OverviewUpload = {
    blockingDatabase.runUnit(database.largeObjectManager.truncate(upload.contentsOid).transactionally)
    upload.withUploadedBytes(0).save
  }

  def cancelUpload(upload: OverviewUpload) = {
    blockingDatabase.run(database.largeObjectManager.unlink(upload.contentsOid).transactionally)
    upload.uploadedFile.delete
    upload.delete
  }
}

