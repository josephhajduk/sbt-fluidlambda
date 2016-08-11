// this bit is important
sbtPlugin := true

organization := "com.solidys"

name := "sbt-fluidlambda"

version := "0.1-SNAPSHOT"

//scalaVersion := " 2.10.4,"

val aws_version = "1.11.24"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-lambda" % aws_version
libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % aws_version

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.11")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")