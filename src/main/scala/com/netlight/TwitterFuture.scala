package com.netlight

import com.datastax.driver.core.{ResultSet, ResultSetFuture}
import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.twitter.util.{Promise, Future}

/**
 * Using Guava to get twitter Future for Cassandra results allowing for idiomatic scala syntax
 */
object TwitterFuture {

  /**
   * Using a promise to complete with result of the Guava future
   */
  implicit def toTwitterFuture(future: ResultSetFuture): Future[ResultSet] = {
    val promise = Promise[ResultSet]()

    Futures.addCallback(future, new FutureCallback[ResultSet] {
      override def onFailure(throwable: Throwable): Unit = promise.raise(throwable)
      override def onSuccess(rs: ResultSet): Unit = promise.setValue(rs)
    })
    promise
  }
}