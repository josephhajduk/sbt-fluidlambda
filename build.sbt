/*
 * Copyright 2016 Solidys Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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