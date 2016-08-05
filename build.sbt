// this bit is important
sbtPlugin := true

organization := "com.solidys"

name := "sbt-fluidlambda"

version := "0.1-SNAPSHOT"

//scalaVersion := " 2.10.4,"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-lambda" % "1.11.22"
