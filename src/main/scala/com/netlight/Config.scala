package com.netlight

object Config {
  val keySpace = "shorturlkeyspace"
  val cassandraPort = 9042
  val cassandraHost  = "localhost"

  val longUrlTable = "long_urls"
  val shortUrlTable = "short_urls"

  val longUrlCol = "long_url"
  val shortUrlCol = "short_url"

  val nodeId = 123
}
