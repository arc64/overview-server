package com.overviewdocs.tree.orm.finders

import org.squeryl.Query
import com.overviewdocs.postgres.SquerylEntrypoint
import scala.language.implicitConversions

/** An SQL query with useful conversions.
  *
  * A FinderResult is a query that has not yet been run. It may be created
  * outside of a database transaction.
  *
  * When a FinderResult is cast to an Iterable, the query is executed. At that
  * time, a database connection must be open. The conversion may be implicit.
  */
class FinderResult[A](val query: Query[A]) {
  def toQuery : Query[A] = query
  def toIterable : Iterable[A] = {
    SquerylEntrypoint.queryToIterable(query)
  }
  def headOption : Option[A] = query.headOption // avoid double implicit conversion
  def count : Long = {
    import com.overviewdocs.postgres.SquerylEntrypoint._
    val countQuery = from(query)(_ => compute(SquerylEntrypoint.count))
    countQuery.headOption.getOrElse(throw new AssertionError("Count did not return anything")).measures
  }
}
object FinderResult {
  def apply[A](query: Query[A]) = new FinderResult(query)

  implicit def finderResultToQuery[A](r: FinderResult[A]): Query[A] = r.toQuery
  implicit def finderResultToIterable[A](r: FinderResult[A]): Iterable[A] = r.toIterable
}
