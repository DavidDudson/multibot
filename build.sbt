import sbt.Keys._
import sbt._
import sbtbuildinfo.Plugin._

name := "multibot"

version := "1.0"

fork in run := true

connectInput in run := true

mainClass in Compile := Some("org.multibot.Multibottest")

updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)

publishArtifact in(Compile, packageDoc) := false
resolvers += Resolver.bintrayRepo("scalameta", "maven")
resolvers += Resolver.jcenterRepo

assemblyJarName in assembly := "metabot.jar"

herokuAppName in Compile := "metabot1"

scalaVersion := "2.12.1"

herokuFatJar in Compile := Some((assemblyOutputPath in assembly).value)

TaskKey[Unit]("stage") := herokuFatJar

dependencyOverrides += "com.squareup.okio" % "okio" % "1.11.0"

libraryDependencies += "org.scalameta" %% "scalameta" % "1.7.0"
libraryDependencies += "org.scalameta" %% "contrib" % "1.7.0"

libraryDependencies ++= Seq(
  "com.github.amatkivskiy" % "gitter.sdk.async" % "1.6.0",
  "com.github.amatkivskiy" % "gitter.sdk.sync" % "1.6.0",
  "org.slf4j" % "slf4j-simple" % "1.7.10",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.http4s" %% "http4s-client" % "0.15.0a",
  "org.json4s" %% "json4s-native" % "3.5.0",
  "com.google.guava" % "guava" % "18.0",
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

autoCompilerPlugins := true

scalacOptions ++= Seq("-feature:false", "-language:_", "-deprecation", "-Xexperimental")

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, scalacOptions in(Compile, compile), libraryDependencies in(Compile, compile))

buildInfoPackage := "org.multibot"

