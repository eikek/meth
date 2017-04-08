import meth.sbt.libs._

name := "meth"
version := "0.0.1-SNAPSHOT"
scalaVersion := `scala-version`

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-Xfatal-warnings", // fail when there are warnings
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:higherKinds",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import"
)

libraryDependencies ++= Seq(
  `scalaj-http`, xz, `fs2-core`, `scalaj-http`, scopt, pureconfig,
  `jackson-core`, fastparse, scalatest
)

resources in Compile += baseDirectory.value/"README.md"

assemblyOption in assembly := (assemblyOption in assembly).value.copy(
  prependShellScript = Some(
    Seq("#!/usr/bin/env sh", """exec java -jar -XX:+UseG1GC $METH_JAVA_OPTS "$0" "$@"""")
  )
)

assemblyJarName in assembly := s"${name.value}-${version.value}-${scalaVersion.value}"
