/**
 * KMeansComponentsDocTreeBuilder.scala
 * Break documents into connected components that are very similar,
 * then cluster the components using recursive variable k-means
 *
 * Overview, created March 2013
 *
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering

import scala.collection.mutable

import com.overviewdocs.nlp.DocumentVectorTypes._
import com.overviewdocs.nlp.IterativeKMeansDocuments

class KMeansComponentsDocTreeBuilder(docVecs: DocumentSetVectors, k:Int) {
  protected def makeNestedProgress(onProgress: Double => Unit, start: Double, end: Double): Double => Unit = {
    (inner) => onProgress(start + inner * (end - start))
  }

  private val kmComponents = new IterativeKMeansDocumentComponents(docVecs)
  private val kmDocs = new IterativeKMeansDocuments(docVecs)

  // ---- Config ----
  val minComponentFractionForOwnNode = 0.2  // when component is at least this fraction of docs in node, it gets its own child
  val minSplitSize = 2   // keep breaking into clusters until <= this many docs in a node
  val maxDepth = 10      // ...or we reach depth limit

  // Create a new node from a set of components
  private def newNode(components:Iterable[DocumentComponent]) : DocTreeNode = {
    val componentsSet = mutable.Set().++(components)  // first convert to set, then use that to get docs, as the Iterable may be slow (e.g. a view)
    val docs = mutable.Set(componentsSet.view.toSeq.flatMap(_.docs):_*)
    val n = new DocTreeNode(docs)
    n.components = componentsSet
    n
  }

  // Cluster the components within a single node, like this:
  //  - if there are one or zero components, split the docs via k-means
  //  - if there are any sufficiently large components, they each get their own node
  //  - all other components are grouped into new child nodes, via k-means
  private def splitNode(node:DocTreeNode) : Unit = {
    val components = node.components

    if (components.size < 2) {
      // Zero or one components, we are clustering docs directly from this point down
      val stableDocs = node.docs.toArray.sorted   // sort documentIDs, to ensure consistent input to kmeans
      val assignments = kmDocs(stableDocs, k)
      for (i <- 0 until k) {
        val docsInNode = kmDocs.elementsInCluster(i, stableDocs, assignments)  // document IDs assigned to cluster i, lazily produced
        if (docsInNode.size > 0)
          node.children += new DocTreeNode(mutable.Set(docsInNode:_*))
      }

    } else {
      // Two or more components
      // First split out all sufficiently large components into their own children. Count these against the arity.
      val smallComponents = mutable.ArrayBuffer[DocumentComponent]()
      var maxArity = k

      components foreach { component =>
        if (component.nDocs > minComponentFractionForOwnNode * node.docs.size) {
          val child = new DocTreeNode(mutable.Set(component.docs:_*))
          // child.description = "B "  // component split off by itself
          node.children += child
          maxArity -= 1
        } else {
          smallComponents += component
        }
      }

      // Now divide remaining remaining components (if any) among child nodes, that is, cluster the components
      if (smallComponents.size > 0) {
        if (maxArity <= 1) {
          node.children += newNode(smallComponents) // we've already used at least k-1 nodes for big components, all the rest go in one node
        } else {
          val assignments = kmComponents(smallComponents, maxArity)
          for (i <- 0 until maxArity) {
            val componentsInNode = kmComponents.elementsInCluster(i, smallComponents, assignments)
            if (componentsInNode.size > 0) {
              val child = newNode(componentsInNode)
              // child.description = if (componentsInNode.size > 1) "M " else "C "   // "Merged component" vs "individual Component"
              node.children += child
            }
          }
        }
      }
    }

    if (node.children.size == 1)    // if all docs went into single node, make this a leaf, we are done
      node.children.clear           // (either "really" only one cluster, or clustering alg problem, but let's never infinite loop)
  }

  // Split the given node, which must contain the documents from all given components.
  private def splitNode(node:DocTreeNode, level:Integer, onProgress: Double => Unit) : Unit = {
    onProgress(0)

    if ((level < maxDepth) && (node.docs.size >= minSplitSize)) {
      splitNode(node)

      if (!node.children.isEmpty) {
        // recurse, computing progress along the way
        var i=0
        var denom = node.children.size.toDouble
        node.children foreach { node =>
          splitNode(node, level+1, makeNestedProgress(onProgress, i/denom, (i+1)/denom))
          i+=1
        }
      }
    }

    node.components.clear     // we are done with those components, if any, so empty that list

    onProgress(1)
  }

  private def makeComponents(docs:Iterable[DocumentID]) : mutable.Set[DocumentComponent] = {
    val threshold = 0.5                       // docs more similar than this will be in same component
    val numDocsWhereSamplingHelpful = 5000
    val numSampledEdgesPerDoc = 500

    val cc = new ConnectedComponentsDocuments(docVecs)

    if (docVecs.size > numDocsWhereSamplingHelpful)
      cc.sampleCloseEdges(numSampledEdgesPerDoc, threshold) // use sampled edges if the docset is large

    val components = mutable.Set[DocumentComponent]()
    cc.foreachComponent(docs, threshold) {
      components += new DocumentComponent(_, docVecs)
    }

    components
  }

  // ---- Main ----

  def BuildTree(root:DocTreeNode, onProgress: Double => Unit): DocTreeNode = {
    onProgress(0)

    root.components = makeComponents(root.docs)

    onProgress(0.2)

    splitNode(root, 1, makeNestedProgress(onProgress, 0.2, 1.0)) // root is level 0, so first split is level 1

    root
  }
}
