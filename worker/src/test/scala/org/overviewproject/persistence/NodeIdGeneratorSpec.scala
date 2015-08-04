package com.overviewdocs.persistence

import org.specs2.mutable.Specification

class NodeIdGeneratorSpec extends Specification {

  "NodeIdGenerator" should {
    "generate an id based on the document set id and tree index" in {
      val documentSetId = 1l
      val treeIndex = 5l
      val treeId = (documentSetId << 32) | treeIndex
      val expectedNodeId = (documentSetId << 32) | (treeIndex << 20) | 1

      val nodeIdGenerator = new NodeIdGenerator(treeId)

      nodeIdGenerator.rootId must beEqualTo(expectedNodeId)
      nodeIdGenerator.next must be equalTo(expectedNodeId)
      nodeIdGenerator.next must beEqualTo(expectedNodeId + 1)
    }
  }
}
