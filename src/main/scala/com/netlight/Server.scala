package com.netlight

import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.path._
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.http.{RichHttp, Http, Response, Request}
import com.twitter.finagle.Service
import com.twitter.util.{Futures, Future}

import java.net.{URL, InetSocketAddress}

import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpMethod}

object Server {
  import Config._
  import Cassandra.cluster
  import TwitterFuture.toTwitterFuture // implicit conversion of resultsetfuture to twitter futures

  Cassandra bootstrap cluster

  lazy val session = cluster connect keySpace

  val shortUrlsQuery = session.prepare(s"SELECT $longUrlCol FROM $shortUrlTable WHERE $shortUrlCol=?")

  val longUrlsQuery = session.prepare(s"SELECT $shortUrlCol FROM $longUrlTable WHERE $longUrlCol=?")

  val shortUrlsInsert = session.prepare(s"INSERT INTO $shortUrlTable ($shortUrlCol, $longUrlCol) VALUES (?, ?) IF NOT EXISTS")

  val longUrlsInsert = session.prepare(s"INSERT INTO $longUrlTable ($longUrlCol, $shortUrlCol) VALUES (?, ?)")

  /**
   * For the lookup endpoint:
   */
  val lookupService: Service[Request, Response] = new Service[Request, Response] {

    override def apply(request: Request): Future[Response] =
      session.executeAsync(shortUrlsQuery.bind(request.path.drop(1))) map { // request.path.drop(1) not great
        _.one().getString(longUrlCol)
      } map {
        toResponse
      } rescue {
        case throwable: Throwable => Future(Response(HttpResponseStatus.NOT_FOUND))
      }

    def toResponse(url: String): Response = {
      val response = Response(HttpResponseStatus.FOUND)
      response.location = url
      response
    }
  }

  /**
   * For the create endpoint:
   */
  val createService: Service[Request, Response] = new Service[Request, Response] {

    override def apply(request: Request): Future[Response] = {
      val longUrl = Option(request.getParam("url"))

      // Todo: Input validation - currently must format input with http:// to work

      Future(longUrl.get) flatMap {
        findShortUrl
      } flatMap {
        case Some(shortUrl) => Future(shortUrl)
        case None => createShortUrlFor(longUrl.get)
      } map { // Should probably differentiate between creating and returning already existing
        toResponse
      } rescue {
        case _: NoSuchElementException => Future(Response(HttpResponseStatus.BAD_REQUEST))
        case _ => Future(Response(HttpResponseStatus.INTERNAL_SERVER_ERROR))
      }
    }

    def findShortUrl(longUrl: String): Future[Option[String]] =
      session.executeAsync(longUrlsQuery.bind(longUrl))
        .map( result =>
        Option(result.one())
          .map(_.getString(shortUrlCol)))

    def toResponse(shortUrl: String): Response = {
      val response = Response(HttpResponseStatus.CREATED)
      response.setContentString(shortUrl)
      response
    }

    /**
     * Needs improvement see README
     */
    def createShortUrlFor(longUrl: String): Future[String] = {
      val shortUrl = generateShortUrl

      session executeAsync shortUrlsInsert.bind(shortUrl, longUrl) map {
        _.wasApplied() match {
          case true =>
            session executeAsync longUrlsInsert.bind(longUrl, shortUrl)
            shortUrl
          case false =>
            throw new Exception("Not created") // Expensive to throw exceptions ...
        }
      } rescue { // Can end up in deep recursive calls and cause run out of memory
        case _ => createShortUrlFor(longUrl)
      }
    }

    /**
     * Needs improvement see README
     */
    def generateShortUrl: String = {
      s"${System.currentTimeMillis()}$nodeId"
    }
  }

  val router = RoutingService.byMethodAndPathObject[Request] {
    case (HttpMethod.GET, Root / shortUrl) => lookupService
    case (HttpMethod.POST, Root) => createService
  }

  def main(args: Array[String]) {
    println(s"Serving HTTP server on ${Root.toString()} 9000")

    ServerBuilder()
      .codec(RichHttp[Request](Http()))
      .bindTo(new InetSocketAddress(9000))
      .name("Server")
      .build(router)
  }
}
