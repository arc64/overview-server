package models.upload

import helpers.DbTestContext
import java.sql.Timestamp
import java.util.UUID
import org.postgresql.PGConnection

import org.overviewproject.postgres.LO
import org.overviewproject.test.DbSpecification

class OverviewUploadSpec extends DbSpecification {
  "OverviewUpload" should {
    trait UploadContext extends DbScope {
      val guid = UUID.randomUUID
      val contentDisposition = "attachment; filename=file"
      val contentType = "text/csv"
      val totalSize = 42l
      val chunk: Array[Byte] = Array(0x12, 0x13, 0x14)
      var userId = 1l

      implicit val implicitPgConnection = pgConnection

      sql("""
        INSERT INTO "user" (id, email, role, password_hash, confirmed_at, email_subscriber, tree_tooltips_enabled)
        VALUES (1, 'admin@overview-project.org', 2, '$2a$07$ZNI3MdA1MK7Td2w1EKpl5u38nll/MvlaRfZn0S8HLerNuP2hoD5JW', TIMESTAMP '1970-01-01 00:00:00', FALSE, FALSE);
      """)

      connection.setAutoCommit(false) // for LO
    }

    "create uploadedFile" in new UploadContext {
      LO.withLargeObject { lo =>
        val before = new Timestamp(System.currentTimeMillis)
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid)

        upload.lastActivity.compareTo(before) must beGreaterThanOrEqualTo(0)
        upload.size must be equalTo (totalSize)
        upload.contentsOid must be equalTo (lo.oid) 
        upload.uploadedFile.contentDisposition must be equalTo (contentDisposition)
        upload.uploadedFile.contentType must be equalTo (contentType)
        upload.uploadedFile.size must be equalTo (0)
        upload.uploadedFile.uploadedAt.compareTo(before) must beGreaterThanOrEqualTo(0)
      }
    }

    "update bytesUploaded" in new UploadContext {
      LO.withLargeObject { lo =>
        val before = new Timestamp(System.currentTimeMillis)
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid)

        val uploadedSize = lo.add(chunk)

        val updateTime = new Timestamp(System.currentTimeMillis)
        val updatedUpload = upload.withUploadedBytes(uploadedSize)

        updatedUpload.lastActivity.compareTo(updateTime) must beGreaterThanOrEqualTo(0)
        updatedUpload.uploadedFile.size must be equalTo (uploadedSize)
      }
    }

    "be saveable and findable by (userid, guid)" in new UploadContext {
      LO.withLargeObject { lo =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid)
        upload.contentsOid must be equalTo(lo.oid)
        upload.save
      }

      val found = OverviewUpload.find(userId, guid)
      found must beSome
    }

    "leave valid uploadedFile when deleted" in new UploadContext {
      val uploadedFileId = LO.withLargeObject { lo =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid)
        upload.save
        upload.delete
        upload.uploadedFile.id
      }

      OverviewUpload.find(userId, guid) must beNone
      OverviewUploadedFile.findById(uploadedFileId) must beSome
    }

    "truncate large object" in new UploadContext {
      LO.withLargeObject { lo =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid).withUploadedBytes(234)
        val truncatedUpload = upload.truncate
        truncatedUpload.uploadedFile.size must be equalTo (0)
      }
    }

    "save changes in uploadedFile" in new UploadContext {
      val fileSize = 234

      LO.withLargeObject { lo =>
        val upload = OverviewUpload(userId, guid, contentDisposition, contentType, totalSize, lo.oid).withUploadedBytes(fileSize)
        upload.save
        val uploadedFile = OverviewUploadedFile.findById(upload.uploadedFile.id)
        uploadedFile must beSome.like { case u => u.size must be equalTo (fileSize) }
      }
    }

  }
}
