package com.overviewdocs.jobhandler.filegroup.task

import java.nio.file.Path
import java.nio.file.Paths

/** Refers to the temporary directory where the documentset-worker stores files during document conversion */
object TempDirectory {

  def path: Path = {
    val tmpDir = System.getProperty("java.io.tmpdir")
    Paths.get(tmpDir, "overview-documentset-worker")
  }
  
  def filePath(name: String): Path = path.resolve(name)

  def create: Unit = DirectoryCleaner.createCleanDirectory(path)
}