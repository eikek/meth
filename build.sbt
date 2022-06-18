import meth.sbt.libs._

name := "meth"
version := "0.0.5-SNAPSHOT"
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
Compile / console / scalacOptions ~= (_ filterNot (Set("-Xfatal-warnings", "-Ywarn-unused-import").contains))

libraryDependencies ++= Seq(
  `scalaj-http`, xz, `fs2-core`, `scalaj-http`, scopt, pureconfig,
  `jackson-core`, fastparse, scalatest
)

Compile / resources += baseDirectory.value/"README.md"

assembly / assemblyOption := (assembly / assemblyOption).value.copy(
  prependShellScript = Some(
    Seq("#!/usr/bin/env sh", """exec java -jar -XX:+UseG1GC $METH_JAVA_OPTS "$0" "$@"""")
  )
)

assembly / assemblyJarName := s"${name.value}-${version.value}"
