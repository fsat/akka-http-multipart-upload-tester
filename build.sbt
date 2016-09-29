import sbt.Resolver.bintrayRepo

name := "akka-http-multipart-upload-tester"

resolvers += bintrayRepo("typesafe", "maven-releases")

version := "0.1.0"

scalaVersion := "2.11.8"

scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

libraryDependencies ++= List(
  "com.typesafe.akka"      %% "akka-http-experimental"  % "2.4.10",
  "com.typesafe.akka"      %% "akka-contrib-extra"      % "3.3.0"
)
