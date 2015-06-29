package org.overviewproject.test

import org.overviewproject.http.SimpleResponse

case class TestSimpleResponse(
  override val status: Int,
  val body: String,
  simpleHeaders: Map[String, String] = Map()
) extends SimpleResponse {
  override def bodyAsBytes = body.getBytes("utf-8")
  override def headers(name: String): Seq[String] = simpleHeaders.get(name) match {
    case Some(value) => Seq(value)
    case _ => Seq.empty
  }
  override def headersToString: String = simpleHeaders.map { case (k, v) => s"$k:$v"}.mkString("\r\n")
}
