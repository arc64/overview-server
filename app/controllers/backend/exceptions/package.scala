package controllers.backend

import java.sql.SQLException
import org.apache.lucene.queryparser.classic.ParseException
import org.elasticsearch.action.search.{SearchPhaseExecutionException,ShardSearchFailure}
import org.elasticsearch.{ElasticsearchException,ElasticsearchParseException}

package object exceptions {
  /** You tried to duplicate a primary key or unique index tuple. */
  class Conflict(t: Throwable) extends Exception(t)

  /** You referenced a missing parent object through a foreign key, or you are
    * trying to delete a parent object when children exist. */
  class ParentMissing(t: Throwable) extends Exception(t)

  /** Runs the given code, possibly re-throwing exceptions as Conflict or
    * ParentMissing. */
  def wrap[T](fn: => T) = try {
    fn
  } catch {
    case e: SQLException => e.getSQLState() match {
      case "23505" => throw new Conflict(e)
      case "23503" => throw new ParentMissing(e)
      case _ => throw e
    }
  }
}
