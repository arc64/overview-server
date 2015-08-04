package com.overviewdocs.nlp

import java.io.InputStream
import java.io.ByteArrayInputStream
import com.overviewdocs.util.Logger

object StopWordSet {
  private val logger = Logger.forClass(getClass)
  private val DefaultStopWordsFile: String = "/stopwords-en.csv"
  private val EmptyStopWordsStream: InputStream = new ByteArrayInputStream(Array.empty)

  def apply(lang: String, suppliedStopWordString: Option[String]): Set[String] = {
    val stopWordLines = io.Source.fromInputStream(stopWordsFile(lang))(io.Codec.UTF8).getLines
    val suppliedStopWords = extractStopWords(suppliedStopWordString)
    
    stopWordLines.toSet ++ suppliedStopWords
  }

  private def stopWordsFile(lang: String): InputStream = {
    val filename = s"/stopwords-$lang.csv"
    val possibleStopWordStreams: Seq[InputStream] = Seq(filename, DefaultStopWordsFile).map(fileAsStream)

    possibleStopWordStreams.find(_ != null).getOrElse {
      logger.error("No stopwords file found")
      EmptyStopWordsStream
    }
  }

  private def fileAsStream(filename: String): InputStream = getClass.getResourceAsStream(filename)

  private def extractStopWords(wordString: Option[String]): Set[String] = wordString.map { s =>
    val separators = """[\s\u00A0,]+""".r
    val allowedPunctuation = "-'"
    def allowedChar(c: Char): Boolean = c.isDigit || c.isLetter || allowedPunctuation.contains(c)

    val words = separators.split(s.trim).toSet
    
    words.map(_.filter(allowedChar).toLowerCase)
  }.getOrElse(Set.empty[String])

}
