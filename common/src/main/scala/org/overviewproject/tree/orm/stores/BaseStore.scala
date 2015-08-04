package com.overviewdocs.tree.orm.stores

import org.squeryl.{ KeyedEntity, KeyedEntityDef, Query, Table }
import org.squeryl.dsl.QueryDsl

import com.overviewdocs.postgres.SquerylEntrypoint._

class BaseStore[A](protected val table: Table[A]) {
  implicit protected val ked: KeyedEntityDef[A,_] = table.ked.getOrElse(throw new AssertionError("Need KeyedEntityDef"))

  def insertOrUpdate(a: A): A = {
    table.insertOrUpdate(a)
  }

  def insertBatch(as: Iterable[A]) : Unit = {
    table.insert(as)
  }

  /** FIXME this is not type-safe. Be sure T relates to A properly. */
  def insertSelect[T](q: Query[T]) : Int = {
    table.insertSelect(q)
  }

  def delete(query: Query[A]): Int = {
    table.delete(query)
  }

  /** Deletes the object by the given key.
    *
    * Warning: you _must_ import com.overviewdocs.postgres.SquerylEntrypoint._
    * before calling this method, or it will not compile.
    */
  def delete[K](k: K)(implicit ked: KeyedEntityDef[A,K]) : Unit = {
    table.delete(k)
  }
}


object BaseStore {
  def apply[A](table: Table[A]) = new BaseStore(table)
}
