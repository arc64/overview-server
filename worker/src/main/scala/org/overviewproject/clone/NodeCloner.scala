package org.overviewproject.clone

import org.overviewproject.tree.orm.Node

object NodeCloner {
  def clone(sourceDocumentSetId: Long, cloneDocumentSetId: Long): Map[Long, Long] = {
    import persistence.Schema
    import org.overviewproject.postgres.SquerylEntrypoint._


    def cloneSubTree(sourceParentNode: Node, nodes: Iterable[Node], nodeIds: Map[Long, Long]): Map[Long, Long] = {
      val cloneParentId = sourceParentNode.parentId.flatMap(nodeIds.get(_))

      // TODO: clone document cache
      val cloneParentNode = sourceParentNode.copy(id = 0l, documentSetId = cloneDocumentSetId, parentId = cloneParentId)
      Schema.nodes.insert(cloneParentNode)

      val parentIdMap = Map(sourceParentNode.id -> cloneParentNode.id)

      val (childNodes, subTreeNodes) = nodes.partition(_.parentId.exists(_ == sourceParentNode.id))
      
      val subTreeMaps = childNodes.map(cloneSubTree(_, subTreeNodes, parentIdMap))
      
      subTreeMaps.fold(parentIdMap)(_ ++ _)
    }

    val sourceNodes = Schema.nodes.where(n => n.documentSetId === sourceDocumentSetId)

    val (rootNode, subTreeNodes) = sourceNodes.partition(_.parentId.isEmpty)
    rootNode.headOption.map { cloneSubTree(_, subTreeNodes, Map()) }.getOrElse(Map.empty)
  }

}