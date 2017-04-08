package meth.sbt

import sbt._

object libs {

  val `scala-version` = "2.12.1"

  // https://github.com/lihaoyi/fastparse
  // MIT
  val fastparse = "com.lihaoyi" %% "fastparse" % "0.4.2"

  // https://github.com/FasterXML/jackson-core
  // ASL 2.0
  val `jackson-core` = "com.fasterxml.jackson.core" % "jackson-core" % "2.8.7"

  // http://tukaani.org/xz/java.html
  // Public Domain
  val xz ="org.tukaani" % "xz" % "1.6"

  // https://github.com/pureconfig/pureconfig
  // MPL 2.0
  val pureconfig = "com.github.pureconfig" %% "pureconfig" % "0.7.0"

  // https://github.com/typelevel/cats
  // MIT http://opensource.org/licenses/mit-license.php
  val `cats-core` = "org.typelevel" %% "cats-core" % "0.9.0"

  // https://github.com/functional-streams-for-scala/fs2
  // MIT
  val `fs2-core` = "co.fs2" %% "fs2-core" % "0.9.5"
  val `fs2-io` = "co.fs2" %% "fs2-io" % "0.9.5"

  // https://github.com/scalatest/scalatest
  // ASL 2.0
  val scalatest = "org.scalatest" %% "scalatest" % "3.0.1"

  // https://github.com/lihaoyi/Ammonite
  // MIT
  val ammonite = "com.lihaoyi" % "ammonite" % "0.8.2" cross CrossVersion.full

  // https://github.com/rickynils/scalacheck
  // unmodified 3-clause BSD
  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.13.4"

  // https://etorreborre.github.io/specs2
  //
  val specs2 = "org.specs2" %% "specs2-core" % "3.8.9" % "test"

  // https://github.com/tpolecat/doobie
  // MIT
  val `doobie-core` = "org.tpolecat" %% "doobie-core-cats" % "0.4.1"
  val `doobie-h2` = "org.tpolecat" %% "doobie-h2-cats" % "0.4.1"

  // https://github.com/h2database/h2database
  // MPL 2.0 or EPL 1.0
  val h2 = "com.h2database" % "h2" % "1.4.194"


  val h2lucene = "org.apache.lucene" % "lucene-core" % "3.6.2"

  // https://github.com/circe/circe
  // ASL 2.0
  val `circe-core` = "io.circe" %% "circe-core" % "0.7.0"
  val `circe-generic` = "io.circe" %% "circe-generic" % "0.7.0"
  val `circe-parser` = "io.circe" %% "circe-parser" % "0.7.0"

  // https://github.com/scopt/scopt
  // MIT
  val scopt = "com.github.scopt" %% "scopt" % "3.5.0"

  // https://github.com/typesafehub/scala-logging
  // ASL 2.0
  val `scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"

  // http://logback.qos.ch/
  // EPL1.0 or LGPL 2.1
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.1.9"

  // https://github.com/scalaj/scalaj-http
  // ASL 2.0
  val `scalaj-http` = "org.scalaj" %% "scalaj-http" % "2.3.0"
}
