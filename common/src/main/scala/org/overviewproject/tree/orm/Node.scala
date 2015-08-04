package com.overviewdocs.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._

case class Node(
  val id: Long = 0L,
  val rootId: Long,
  val parentId: Option[Long],
  val description: String,
  val cachedSize: Int,
  val isLeaf: Boolean) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = true // use Schema's insert() to insert
}
