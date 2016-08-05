// this bit is important
sbtPlugin := true

organization := "com.solidys"

name := "sbt-fluidlambda"

version := "0.1-SNAPSHOT"

//scalaVersion := " 2.10.4,"

libraryDependencies += "com.amazonaws" % "aws-java-sdk-lambda" % "1.11.22"

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.11")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")