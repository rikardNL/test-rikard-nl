package com.netlight

import com.twitter.conversions.time._
import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Futures}
import org.specs2.mutable.Specification


class ServerSpec extends Specification {
  // An incomplete set of tests
  // Requires Cassandra instance to be running

  Cassandra bootstrap Cassandra.cluster

  val timeout = 10 seconds

  "LookupService should" >> {
    "Return 404 - Not found response when short url not found" >> {
      val response = Await.result(Server.lookupService(Request("/notavailable")), timeout)
      response.getStatusCode() mustEqual 404
    }

    "Return 302 - Redirect when shortUrl is in the database" >> {

      val shortUrl = System.currentTimeMillis().toString
      val longUrl = s"http://example.com/${System.currentTimeMillis()}"

      Cassandra.cluster
        .connect(Config.keySpace)
        .execute(
            s"""
              INSERT INTO ${Config.shortUrlTable}
              (${Config.shortUrlCol}, ${Config.longUrlCol})
              VALUES ('$shortUrl', '$longUrl')
            """)

      val response = Await.result(Server.lookupService(Request(s"/$shortUrl")), timeout)
      response.getStatusCode() mustEqual 302
      response.location mustEqual Some(longUrl)
    }
  }

  "CreateService should" >> {
    "Return the short url as content in 200 - Created response" >> {
      val longUrl = s"http://example.com/${System.currentTimeMillis()}"

      val response = Await.result(Server.createService(Request("url" -> longUrl)), timeout)
      response.getStatusCode() mustEqual 201
      response.getContentString() must not be empty
    }

    "Return the same short url for a long url when creating it twice" >> {
      val request = Request("url" -> s"http://example.com/${System.currentTimeMillis()}")

      val response1 = Await.result(Server.createService(request), timeout)
      val response2 = Await.result(Server.createService(request), timeout)

      response1.getStatusCode mustEqual 201
      response2.getStatusCode mustEqual 201
      response1.getContentString() mustEqual response2.getContentString()
    }

    "Return a Bad Request when there url field not inputed" >> {
      val response = Await.result(Server.createService(Request()), timeout)

      response.getStatusCode() mustEqual 400
      response.getContentString() must be empty
    }
  }

}
