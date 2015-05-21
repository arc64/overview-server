package org.overviewproject.jobhandler.filegroup.task

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Paths
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.util.control.Exception._

import org.overviewproject.jobhandler.filegroup.task.LibreOfficeDocumentConverter._
import org.overviewproject.util.Configuration

class LibreOfficeDocumentConverterSpec extends Specification with Mockito {
  "LibreOfficeDocumentConverter" should {
    "call conversion command with the correct parameters" in new BaseScope {
      documentConverter.withStreamAsPdf(guid, inputStream)(doNothing _)

      there was one(documentConverter.runner).run(libreOfficeCommand)
    }

    "write the input stream to a temp file which is deleted after conversion" in new BaseScope {

      documentConverter.withStreamAsPdf(guid, inputStream)(doNothing _)

      there was one(documentConverter.fileSystem).saveToFile(inputStream, inputFilePath)
      there was one(documentConverter.fileSystem).deleteFile(inputFile)
    }

    "delete output file after conversion is complete" in new BaseScope {
      documentConverter.withStreamAsPdf(guid, inputStream)(doNothing _)

      there was one(documentConverter.fileSystem).deleteFile(outputFile)
    }

    "call processing function with converted output stream" in new BaseScope {
      val result = documentConverter.withStreamAsPdf(guid, inputStream)(Tuple2.apply)

      result must beEqualTo(documentConverter.convertedStream, documentConverter.convertedSize)
    }

    "delete input file if exception is thrown during processing" in new BaseScope {
      ignoring(classOf[Exception]) { documentConverter.withStreamAsPdf(guid, inputStream)(failingProcessing) }

      there was one(documentConverter.fileSystem).deleteFile(inputFile)
    }

    "throw a ConverterFailedException if office command returns an error" in new BaseScope {
      val failingConverter = new FailingConverter(inputFile)
      failingConverter.withStreamAsPdf(guid, inputStream)(doNothing _) must throwA[LibreOfficeConverterFailedException]
    }

    "delete output file if exception is thrown during processing" in new BaseScope {
      ignoring(classOf[Exception]) { documentConverter.withStreamAsPdf(guid, inputStream)(failingProcessing) }

      there was one(documentConverter.fileSystem).deleteFile(outputFile)
    }

    "throw a NoConverterOutput if no output file is found" in new BaseScope {
      val noOutputConverter = new ConverterWithNoOutput(inputFile)

      noOutputConverter.withStreamAsPdf(guid, inputStream)(doNothing _) must throwA[LibreOfficeNoOutputException]

    }

  }

  trait BaseScope extends Scope {
    def tempDir = {
      val tmpDir = System.getProperty("java.io.tmpdir")
      Paths.get(tmpDir, "overview-documentset-worker")
    }
    val fileGuid = "ccddeeff-1122-3344-5566-77889900aabb"
    val filename = "abcd.doc"
    val outputFileName = s"$fileGuid.pdf"
    val inputFilePath = tempDir.resolve(Paths.get(fileGuid))
    val inputFile = inputFilePath.toFile
    val outputFile = tempDir.resolve(Paths.get(outputFileName)).toFile
    
    val officeCommandPieces = Seq(
      Configuration.getString("libre_office_path"),
      "--headless --nologo --invisible --norestore --nolockcheck --convert-to pdf",
      s"--outdir $tempDir $inputFilePath")
    val libreOfficeCommand = officeCommandPieces.mkString(" ")

    val guid = UUID.fromString(fileGuid)
    val inputStream = mock[InputStream]
    val documentConverter = new TestLibreOfficeDocumentConverter(inputFile)

    def doNothing(inputStream: InputStream, size: Long): Unit = ()

    def failingProcessing(inputStream: InputStream, size: Long): Int = throw new Exception("something went wrong")

  }

  class TestLibreOfficeDocumentConverter(inputFile: File) extends LibreOfficeDocumentConverter {
    override val runner = mock[Runner]
    override val fileSystem = mock[FileSystem]

    val convertedStream = mock[InputStream]
    val convertedSize = 4L

    runner.run(any) returns runResult
    fileSystem.saveToFile(any, any) returns inputFile

    setupReadFile

    def setupReadFile: Unit = {
      fileSystem.readFile(any) returns convertedStream
      fileSystem.getFileLength(any) returns convertedSize
    }

    def runResult: Either[String, String] = Right("Success")
    def readFileResult: InputStream = convertedStream
  }

  class FailingConverter(inputFile: File) extends TestLibreOfficeDocumentConverter(inputFile) {
    override def runResult: Either[String, String] = Left("Failure")
  }

  class ConverterWithNoOutput(inputFile: File) extends TestLibreOfficeDocumentConverter(inputFile) {
     override def setupReadFile: Unit = fileSystem.readFile(any) answers { _ => throw new FileNotFoundException } 
  }
}
