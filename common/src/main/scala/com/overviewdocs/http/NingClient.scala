package com.overviewdocs.http

import com.ning.http.client.{AsyncCompletionHandler,AsyncHttpClient,AsyncHttpClientConfig,FluentCaseInsensitiveStringsMap,Realm,RequestBuilder,Response=>HttpResponse}
import com.ning.http.client.Realm.AuthScheme
import scala.collection.immutable.TreeMap
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.{ExecutionContext,Future,Promise}

/** An HTTP Client based on Ning AsyncHttpClient.
  */
class NingClient extends Client {
  private val Timeout = 5 * 60 * 1000 // 5 minutes, to allow for downloading large files 
  private val NRetries = 3

  @volatile private var shutdownWasCalled = false

  private val config = new AsyncHttpClientConfig.Builder()
    .setAllowPoolingConnections(true)
    .setAllowPoolingSslConnections(true)
    .setCompressionEnforced(true)
    .setConnectTimeout(Timeout)
    .setMaxRequestRetry(NRetries)
    .setRequestTimeout(Timeout)
    .build

  private class Handler extends AsyncCompletionHandler[Unit] {
    private val promise = Promise[Response]()
    val future = promise.future

    private def parseHeaders(in: FluentCaseInsensitiveStringsMap): Map[String,Seq[String]] = {
      var map = new TreeMap[String,Seq[String]]()(Ordering.by(_.toLowerCase))

      for (entry <- iterableAsScalaIterable(in)) {
        val seq: Seq[String] = iterableAsScalaIterable(entry.getValue).toSeq
        map += (entry.getKey -> seq)
      }

      map
    }

    override def onThrowable(ex: Throwable): Unit = {
      if (shutdownWasCalled) {
        promise.failure(new HttpClientShutDownException)
      } else {
        promise.failure(ex)
      }
    }

    override def onCompleted(response: HttpResponse): Unit = {
      if (shutdownWasCalled) {
        promise.failure(new HttpClientShutDownException)
      } else {
        promise.success(Response(
          response.getStatusCode,
          parseHeaders(response.getHeaders),
          response.getResponseBodyAsBytes
        ))
      }
    }
  }

  private val client = new AsyncHttpClient(config)

  override def get(request: Request)(implicit ec: ExecutionContext) = {
    val builder = new RequestBuilder()
      .setMethod("GET")
      .setUrl(request.url)
      .setFollowRedirects(request.followRedirects)

    request.maybeCredentials.foreach { credentials =>
      val realm = new Realm.RealmBuilder()
        .setScheme(AuthScheme.BASIC)
        .setPrincipal(credentials.username)
        .setPassword(credentials.password)
        .setUsePreemptiveAuth(true)
        .build

      builder.setRealm(realm)
    }

    val ningRequest = builder.build
    val handler = new Handler
    client.executeRequest(ningRequest, handler)
    handler.future
  }

  def shutdown: Unit = {
    shutdownWasCalled = true
    client.close
  }
}
