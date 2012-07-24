package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SubTreeLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  trait MockComponents extends Scope {
    val loader = mock[SubTreeDataLoader]
    val parser = mock[SubTreeDataParser]
    val subTreeLoader = new SubTreeLoader(1, 4, loader, parser)
    
    val dummyNodes = List(core.Node(1, "dummyNode", Nil, null), 
    		  			  core.Node(2, "dummyNode", Nil, null), 
    		  			  core.Node(3, "dummyNode", Nil, null))	

  }
  
  "SubTreeLoader" should {
    
    "call loader and parser to create nodes" in new MockComponents {
      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"))
      loader loadNodeData(1, 4) returns nodeData
      parser createNodes(nodeData, Nil) returns dummyNodes
      
      val nodes = subTreeLoader.loadNodes()
      
      there was one(loader).loadNodeData(1, 4)
      there was one(parser).createNodes(nodeData, Nil)
      
      nodes must be equalTo(dummyNodes)
    }
    
    "call loader and parser to create documents from nodes" in new MockComponents {
      val documentIds = List(10l, 20l, 30l)
      val nodeIds = List(1l, 2l, 3l)
      
      val mockDocumentData = List((10l, "actually", "all", "documentdata"))
      val mockDocuments = List(core.Document(10l, "documents", "created", "from data"))
      
      loader loadDocuments(nodeIds) returns mockDocumentData
      parser createDocuments(mockDocumentData) returns mockDocuments
      
      val documents = subTreeLoader.loadDocuments(dummyNodes)

      there was one(loader).loadDocuments(nodeIds)
      there was one(parser).createDocuments(mockDocumentData)
    }
    
    "not duplicate documents included in multiple nodes" in new MockComponents {
      val documentIds = List(10l, 20l, 30l, 10l, 40l, 10l, 40l, 40l, 50l)
      val nodeIds = List(1l, 2l, 3l)
      
      val dummyDocumentData = documentIds.map((_, "nodes will", "include the", "same documents"))
      val dummyDocuments = List(core.Document(10l, "documents", "created", "from data"))
      
      val distinctDocumentData = documentIds.distinct.map((_, "nodes will", "include the", "same documents"))
          
      loader loadDocuments(nodeIds) returns dummyDocumentData
      parser createDocuments(distinctDocumentData) returns dummyDocuments 

      val documents = subTreeLoader.loadDocuments(dummyNodes)
      
      there was one(parser).createDocuments(distinctDocumentData)
    }
    
    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(3, 55)
      
      success
    }
   
  }   
        
}