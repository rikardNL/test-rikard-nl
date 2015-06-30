name := """test-rikard-nl"""

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.twitter" %% "finagle-core" % "6.26.0",
  "com.twitter" %% "finagle-http" % "6.26.0",
  "com.datastax.cassandra"  % "cassandra-driver-core" % "2.1.6",
  "com.typesafe" % "config" % "1.3.0",
  "org.specs2" %% "specs2-core" % "3.6.1" % "test"
)
