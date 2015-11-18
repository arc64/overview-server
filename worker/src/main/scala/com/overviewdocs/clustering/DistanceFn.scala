/**
 * DistanceFn.scala
 * Cosine Distance implementation
 *
 * Overview, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering
import com.overviewdocs.nlp.DocumentVectorTypes._

// Encapsulates document-document distance function. Returns in range 0 == identical to 1 == unrelated
// ASSUMES document vectors are normalized (we don't check here as this is inner loop code)
object DistanceFn {

  // sparse dot product on two term->float maps
  // Not written in very functional style, as DocumentVector uses an awkward representation as arrays for space reasons
  // Basic intersection of sorted lists algorithm
  private def SparseDot(a: DocumentVector, b: DocumentVector): Double = {
    var a_idx = 0
    var b_idx = 0
    var dot = 0.0

    // Optimize frequently-accessed values
    val a_length = a.length
    var b_length = b.length

    while (a_idx < a_length && b_idx < b_length) {
      val a_term = a.terms(a_idx)
      val b_term = b.terms(b_idx)

      if (a_term < b_term) {
        a_idx += 1
      } else if (b_term < a_term) {
        b_idx += 1
      } else {
        dot += a.weights(a_idx).toDouble * b.weights(b_idx).toDouble
        a_idx += 1
        b_idx += 1
      }
    }

    dot
  }

  // Document distance computation. Returns 1 - similarity, where similarity is cosine of normalized vectors
  def CosineDistance(a: DocumentVector, b: DocumentVector) = {
    1.0 - SparseDot(a, b)
  }


  // Same function for DocumentVectorBuilder. Conceptually identical, actually less efficient due to heavier data structures
  private def SparseDotCore(a:DocumentVectorBuilder, b:DocumentVectorBuilder) : Double = {
    var dot = 0.0
    a foreach { case (termId, aWeight) =>
      val bWeight = b.get(termId)
      if (bWeight.isDefined)
        dot += aWeight.toDouble*bWeight.get.toDouble
    }
    dot
  }

  // Iterate over shorter document vector, for efficiency
  private def SparseDot(a:DocumentVectorBuilder, b:DocumentVectorBuilder) : Double = {
    if (a.size < b.size)
      SparseDotCore(a,b)
    else
      SparseDotCore(b,a)
  }

  def CosineDistance(a:DocumentVectorBuilder, b:DocumentVectorBuilder) : Double = {
     1.0 - SparseDot(a, b)
  }
}
