package controllers.util

import java.io.ByteArrayInputStream
import java.sql.{SQLException, Timestamp}
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{RequestHeader, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers._
import scala.concurrent.Await
import scala.util.Random

import models.upload.{OverviewUpload, OverviewUploadedFile}

class FileUploadIterateeSpec extends test.helpers.InAppSpecification with Mockito {
  "FileUploadIteratee" should {

    /** OverviewUpload implementation that stores data in an attribute */
    case class DummyUpload(userId: Long, guid: UUID, val bytesUploaded: Long, val size: Long, 
      var data: Array[Byte] = Array[Byte](), uploadedFile: OverviewUploadedFile = mock[OverviewUploadedFile]) extends OverviewUpload {
      
      val lastActivity: Timestamp = new Timestamp(0)
      val contentsOid: Long = 1l


      def upload(chunk: Array[Byte]): DummyUpload = {
        data = data ++ chunk
        uploadedFile.size returns (data.size)

        withUploadedBytes(data.size)
      }

      def withUploadedBytes(bytesUploaded: Long): DummyUpload = this.copy(bytesUploaded = bytesUploaded)

      def save: DummyUpload = this
      def truncate: DummyUpload = { this.copy(bytesUploaded = 0, data = Array[Byte]()) }
      def delete {}
    }

    /**
     * Implementation of FileUploadIteratee for testing, avoiding using the database
     */
    class TestIteratee(throwOnCancel: Boolean = false) extends FileUploadIteratee {

      // store the upload as DummyUpload to avoid need for downcasting
      var currentUpload: DummyUpload = _

      var appendCount: Int = 0
      var uploadCancelled: Boolean = false
      var uploadTruncated: Boolean = false

      def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = Option(currentUpload)

      def createUpload(userId: Long, guid: UUID, contentDisposition: String, contentType: String, contentLength: Long): OverviewUpload = {
        uploadCancelled = false
        val uploadedFile = mock[OverviewUploadedFile]
        uploadedFile.contentDisposition returns contentDisposition
        uploadedFile.contentType returns contentType
        
        currentUpload = DummyUpload(userId, guid, 0l, contentLength, uploadedFile = uploadedFile)
        currentUpload
      }

      def cancelUpload(upload: OverviewUpload) {
        if (!throwOnCancel) {
          currentUpload = null
          uploadCancelled = true
        } else throw new SQLException()
      }

      def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): OverviewUpload = {
        appendCount = appendCount + 1

        currentUpload = upload.asInstanceOf[DummyUpload].upload(chunk)
        currentUpload
      }

      def truncateUpload(upload: OverviewUpload): OverviewUpload = {
        uploadTruncated = true
        currentUpload.truncate
      }

      def uploadedData: Array[Byte] = currentUpload.data
    }

    trait UploadContext extends Scope {
      val chunk = new Array[Byte](100)
      Random.nextBytes(chunk)

      val userId = 1l
      val guid = UUID.randomUUID
      val uploadIteratee: TestIteratee

      def input = new ByteArrayInputStream(chunk)

      // implement enumerator in sub-classes to setup specific context
      def enumerator: Enumerator[Array[Byte]]

      def request: RequestHeader
      // Drive the iteratee with the enumerator to generate a result
      def result: Either[Result, OverviewUpload] = {
        implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

        val resultFuture = for {
          doneIt <- enumerator(uploadIteratee.store(userId, guid, request, 15))
          result: Either[Result, OverviewUpload] <- doneIt.run
        } yield result

        Await.result(resultFuture, scala.concurrent.duration.Duration.Inf)
      }

      def upload: OverviewUpload = result.right.get
    }

    // The tests specify:
    // - an Iteratee, that reacts in certain ways
    // - headers, different well-formed headers, as well as bad ones
    // - an enumarator that generates the data in different ways
    // Tests are setup by mixing traits that provide these three components
    // in different combinations

    // Iteraratees
    trait GoodUpload extends UploadContext {
      val uploadIteratee = new TestIteratee
    }

    trait FailingCancel extends UploadContext {
      val uploadIteratee = new TestIteratee(throwOnCancel = true)
    }

    // Headers
    trait UploadHeader {
      val contentDisposition = "attachment; filename=foo.bar"
      val contentType = "text/html; charset=ISO-8859-4"
        
      def headers: Seq[(String, String)]
      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(headers)

        r
      }
    }

    trait GoodHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_RANGE, "0-999/1000"),
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_LENGTH, "100"),
        (CONTENT_TYPE, contentType)  
      )
    }

    trait NoOptionalContentHeader extends UploadHeader {
      def headers = Seq(("Content-Range", "0-999/1000"))
    }

    trait ShortUploadHeader extends UploadHeader {
      def headers = Seq(
        ("Content-Range", "0-29/50"),
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_LENGTH, "100")
      )
    }

    trait BadHeader {
      def request: RequestHeader = FakeRequest()
    }

    trait InProgressHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_LENGTH, "100"),
        (CONTENT_RANGE, "100-199/1000")
        )
    }

    trait MalformedHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_RANGE, "Bad Field")
      )
    }

    // Enumerators
    trait SingleChunk {
      self: UploadContext =>
      def enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input)(Execution.defaultContext)
    }

    trait MultipleChunks {
      self: UploadContext =>
      def enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input, 10)(Execution.defaultContext)
    }

    trait LastChunkWillNotFillBuffer {
      self: UploadContext =>
      def enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input, 8)(Execution.defaultContext)
    }

    "process Enumerator with one chunk only" in new GoodUpload with SingleChunk with GoodHeader {
      result must beRight
      upload.uploadedFile.size must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
    }

    "process Enumerator with multiple chunks" in new GoodUpload with MultipleChunks with GoodHeader {
      upload.uploadedFile.size must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
    }

    "buffer incoming data" in new GoodUpload with MultipleChunks with GoodHeader {
      upload.uploadedFile.size must be equalTo (chunk.size)
      uploadIteratee.appendCount must be equalTo (5)
    }

    "process last chunk of data if buffer not full at end of upload" in new GoodUpload with LastChunkWillNotFillBuffer with GoodHeader {
      upload.uploadedFile.size must be equalTo (chunk.size)
    }

    "truncate upload if restarted at byte 0" in new GoodUpload with SingleChunk with GoodHeader {
      val initialUpload = upload
      val restartedUpload = upload

      restartedUpload.uploadedFile.size must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
      uploadIteratee.uploadTruncated must beTrue
    }

    "set contentDisposition to raw value of Content-Dispositon" in new GoodUpload with SingleChunk with GoodHeader {
      upload.uploadedFile.contentDisposition must be equalTo (contentDisposition)
    }
    
    "set contentType to raw value of Content-Type" in new GoodUpload with SingleChunk with GoodHeader {
      upload.uploadedFile.contentType must be equalTo (contentType)
    }

    "set content* to empty string if no header is found" in new GoodUpload with SingleChunk with NoOptionalContentHeader {
      upload.uploadedFile.contentDisposition must be equalTo ("")
      upload.uploadedFile.contentType must be equalTo ("")
    }

    "return BAD_REQUEST if headers are bad" in new GoodUpload with SingleChunk with BadHeader {
      result must beLeft.like { case r => r.header.status must be equalTo (BAD_REQUEST) }
    }

    "return BAD_REQUEST and cancel upload if CONTENT_RANGE starts at the wrong byte" in new GoodUpload with SingleChunk with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)

      result must beLeft.like { case r => r.header.status must be equalTo (BAD_REQUEST) }
      uploadIteratee.uploadCancelled must beTrue
    }

    "throw exception if cancel fails" in new FailingCancel with SingleChunk with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)

      result must throwA[SQLException]
    }

    "return Upload on valid restart" in new GoodUpload with SingleChunk with InProgressHeader {
      val initialUpload = uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)
      uploadIteratee.appendChunk(initialUpload, chunk)

      result must beRight.like { case u => u.uploadedFile.size must be equalTo (200) }
    }

    "return BAD_REQUEST if headers can't be parsed" in new GoodUpload with SingleChunk with MalformedHeader {
      result must beLeft.like { case r => r.header.status must be equalTo (BAD_REQUEST) }
    }

    "return BAD_REQUEST if upload is longer than expected" in new GoodUpload with SingleChunk with ShortUploadHeader {
      result must beLeft.like { case r => r.header.status must be equalTo (BAD_REQUEST) }
    }
  }
}
