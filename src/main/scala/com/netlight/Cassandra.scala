package com.netlight

import com.datastax.driver.core.Cluster

object Cassandra {

  val cluster: Cluster =
    Cluster.builder()
      .addContactPoint(Config.cassandraHost)
      .withPort(Config.cassandraPort)
      .withClusterName("Test Cluster")
      .build()

  /**
   * Bootstrap cluster with keyspace and tables
   * Blocking - not really relevant code anyway
   */
  def bootstrap(cluster: Cluster): Unit = {
    import com.netlight.Config.{keySpace, shortUrlTable, longUrlTable, shortUrlCol, longUrlCol}

    val initSession = cluster.connect()

    initSession.execute(
      s"""
          CREATE KEYSPACE IF NOT EXISTS $keySpace
          WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """)

    initSession.execute(
      s"""
        CREATE TABLE IF NOT EXISTS $keySpace.$shortUrlTable (
          $shortUrlCol varchar PRIMARY KEY,
          $longUrlCol varchar
        )
      """)

    initSession.execute(
      s"""
        CREATE TABLE IF NOT EXISTS $keySpace.$longUrlTable (
          $longUrlCol varchar PRIMARY KEY,
          $shortUrlCol varchar
        )
      """)

    initSession.close()
  }
}