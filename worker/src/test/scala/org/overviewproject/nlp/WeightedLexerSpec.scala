package com.overviewdocs.nlp
import com.overviewdocs.nlp.DocumentVectorTypes.TermWeight
import org.specs2.mutable.Specification
import com.overviewdocs.util.DisplayedError

class WeightedLexerSpec extends Specification {
  val stopWords = StopWordSet("en", None)

  def makeWeightedTermSeq(s:Seq[(String,TermWeight)]) =
    s map { case (t,w) => WeightedTermString(t,w) }

  "WeightedLexer wihout custom weights" should {

    val wl = new WeightedLexer(stopWords, Map("cat"->5, "cats+"->13, "CAT-mission"->20, "\\w*CAT\\w*"->10, "\\w*DOG\\w*"->10))

    "remove stop words when no matches" in {
      wl.makeTerms("no i haha you") must beEqualTo(makeWeightedTermSeq(Seq(("haha",1))))
    }

    "handle spaces and punct when no matches" in {
      val sentence = "the quick\t  brown.. Fox jump{s over\nyour 500 lAzy"
      val terms = makeWeightedTermSeq(Seq(("quick",1), ("brown",1), ("fox",1), ("jumps",1), ("lazy",1)))
      wl.makeTerms(sentence) must beEqualTo(terms)
    }

    "truncate long words" in {
      val longword = "thequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydog"
      val sentence = "now is the time for all good " + longword + " to come to the aid of their module."
      wl.makeTerms(sentence).map(_.term.length).max must beEqualTo(wl.maxTokenLength)
    }

    "match one simple pattern" in {
      val sentence = "the cat likes tea"
      val terms = makeWeightedTermSeq(Seq(("cat",5), ("likes",1), ("tea", 1)))
      wl.makeTerms(sentence) must beEqualTo(terms)
    }

    "match a regex" in {
      val sentence = "catsss like tea"
      val terms = makeWeightedTermSeq(Seq(("catsss",13), ("like",1), ("tea", 1)))
      wl.makeTerms(sentence) must beEqualTo(terms)
    }

    "match an uppercase-punct regex" in {
      val sentence = "a CAT-mission tomorrow"
      val terms = makeWeightedTermSeq(Seq(("CAT-mission",20), ("tomorrow",1)))
      wl.makeTerms(sentence) must beEqualTo(terms)
    }

    "match multiple regexes at once" in {
      val sentence = "you sir are a weirdCATandDOGmongrel"
      val terms = makeWeightedTermSeq(Seq(("sir",1), ("weirdCATandDOGmongrel", 100)))
      wl.makeTerms(sentence) must beEqualTo(terms)
    }
    
    "throw DisplayedError if regex is invalid" in {
      val sentence = "cats are cats all over the world"
        
      val badWl = new WeightedLexer(stopWords, Map("**" -> 100))
      
      badWl.makeTerms(sentence) must throwA[DisplayedError]
    }
  }
}