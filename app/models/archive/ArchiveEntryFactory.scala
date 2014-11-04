package models.archive

import models.DocumentFileInfo
import java.io.InputStream
import controllers.util.PlayLargeObjectInputStream
import org.overviewproject.models.File
import org.overviewproject.models.Page
import java.io.ByteArrayInputStream
import models.FileViewInfo
import models.PageViewInfo

trait ArchiveEntryFactory {
    
  def createFromFileViewInfos(fileViewInfos: Seq[FileViewInfo]): Seq[ArchiveEntry] = 
    fileViewInfos.map(f => ArchiveEntry(asPdf(f.documentTitle), f.size, largeObjectInputStream(f.viewOid) _))
  
  def createFromPageViewInfos(pageViewInfos: Seq[PageViewInfo]): Seq[ArchiveEntry] = 
    pageViewInfos.map(p =>
      ArchiveEntry(fileNameWithPage(removePdf(p.documentTitle), p.pageNumber), p.size, pageDataStream(p.pageId) _))
      

  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)

  // If the pageId is invalid, NoSuchElementException will be thrown
  // We could return an empty stream, but then the size would be wrong
  // so the archive would be corrupt anyway.
  private def pageDataStream(pageId: Long)(): InputStream =
    storage.pageDataStream(pageId).get

  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    asPdf(s"$fileName p$pageNumber")

  private def removePdf(fileName: String): String = {
    val caseInsensitivePdfExtension = "(?i)\\.pdf$"
    fileName.replaceAll(caseInsensitivePdfExtension, "")
  }
  
  private def asPdf(fileName: String): String = {
    val Pdf = ".pdf"
    if (fileName.toLowerCase endsWith Pdf) fileName
    else fileName + Pdf
  }

  protected val storage: Storage

  protected trait Storage {
    def largeObjectInputStream(oid: Long): InputStream
    def pageDataStream(pageId: Long): Option[InputStream]
  }
}


