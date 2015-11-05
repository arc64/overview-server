package com.overviewdocs.nlp

import org.specs2.mutable.Specification

class StopWordSetSpec extends Specification {
  
  "StopWordSet" should {
    
    "default to English stopwords file if language is empty or unknown" in {
      val stopwords: Set[String] = StopWordSet("", "")
      
      stopwords must contain("the")
    }
    
    "read known stopwords file" in {
      val stopWords: Set[String] = StopWordSet("sv", "")
      
      stopWords must contain("och")
    }
    
    "split supplied stopwords by whitespace and commas" in {
     val (word1, word2, word3, word4) = ("one", "two", "three", "four")
     
      val suppliedStopWordsString = s"  $word1\t  $word2\n\n$word3,$word4\n"
      
      val stopWords: Set[String] = StopWordSet("en", suppliedStopWordsString)
      
      stopWords must contain(word1, word2, word3, word4)
    }
    
    "convert supplied stopwords to lowercase" in {
      val stopWord = "STOPWORD"
        
      val stopWords: Set[String] = StopWordSet("en", stopWord)
      
      stopWords must contain(stopWord.toLowerCase)
    }
    
    "strip all non-alphanumeric chars except - and ' from supplied stopwords" in {
      val (word1, word2, word3) = ("qapla'", "7-of-9", "engage")
      val stopWordString = s"""$word1! ($word2) "$word3""""
      
      val stopWords: Set[String] = StopWordSet("en", stopWordString)
      
      stopWords must contain(word1, word2, word3)
    }
  }

}
