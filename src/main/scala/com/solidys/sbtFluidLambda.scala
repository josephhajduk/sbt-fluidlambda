package com.solidys

import java.io.FileInputStream
import java.nio.ByteBuffer

import sbt._
import Keys._
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.{AWSLambdaClient, model}
import com.amazonaws.services.lambda.model.{CreateFunctionRequest, FunctionCode, UpdateFunctionCodeRequest}
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.PathList

object SbtFluidLambda extends AutoPlugin
{
  override def requires = org.scalajs.sbtplugin.ScalaJSPlugin && sbtassembly.AssemblyPlugin

  // from scala.js plugin
  val fullOptJS =  org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.fullOptJS
  val packageJSDependencies = org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.packageJSDependencies

  // from assembly
  val assembly  = sbtassembly.AssemblyKeys.assembly
  val assemblyMergeStrategy = sbtassembly.AssemblyKeys.assemblyMergeStrategy

  object autoImport {
    val createLambda = taskKey[Unit]("Creates an amazon lambda for the given project")
    val updateLambda = taskKey[Unit]("Updates the code for a lambda if the definition hasn't changed")
    val removeLambda = taskKey[Unit]("Removes the lambda")

    val prepareJVMPayload = taskKey[File]("Creates the JVM source code payload")
    val prepareNodePayload = taskKey[File]("Creates the NODE source code payload")

    val lambdaDisableJVM = SettingKey[Boolean]("Suppresses the creation of the JVM lambda")
    val lambdaDisableNode = SettingKey[Boolean]("Suppresses the creation of the Node lambda")
    val lambdaExecutionRole = SettingKey[String]("lambda arn execution role")
    val lambdaAwsRegion = SettingKey[String]("AWS region to deploy to")
    val lambdaHandler = SettingKey[String]("handler object")

    lazy val baseSettings: Seq[Def.Setting[_]] = Seq(
      prepareNodePayload in Compile <<= (fullOptJS in Compile, packageJSDependencies in Compile, target) map {
        (jsFile, depsFile, tf) =>

          val zipFile = tf / "lambda.zip"
          val inputs: Seq[(File, String)] = Seq((jsFile.data, "index.js"))

          IO.zip(inputs, zipFile)

          zipFile
      },

      prepareJVMPayload in Compile <<= (assembly in Compile) map {
        (jar) => {
          jar
        }
      },

      createLambda in Compile <<= (
        name,
        organization,
        version,
        prepareJVMPayload in Compile,
        prepareNodePayload in Compile,
        lambdaDisableJVM,
        lambdaDisableNode,
        lambdaExecutionRole,
        lambdaAwsRegion,
        lambdaHandler) map {
        (n, o, v, jvmPayload, nodePayload, disableJVM, disableNode, execRole, region, handler) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          //val jvmFS = new FileInputStream(jvmPayload)
          //val jvmBuffer = ByteBuffer.wrap(Stream.continually(jvmFS.read).takeWhile(_ != -1).map(_.toByte).toArray)

          //val nodeFS = new FileInputStream(nodePayload)
          //val nodeBuffer = ByteBuffer.wrap(Stream.continually(nodeFS.read).takeWhile(_ != -1).map(_.toByte).toArray)

          val namePattern = "(arn:aws:lambda:)?([a-z]{2}-[a-z]+-\\d{1}:)?(\\d{12}:)?(function:)?([a-zA-Z0-9-_]+)(:(\\$LATEST|[a-zA-Z0-9-_]+))?"

          val nameBase = o + "_" + n + "_" + handler + "_" + v
          val nameJVM = (nameBase + "_JVM").replace(".", "-")
          val nameNode = (nameBase + "_NODE").replace(".", "-")

          if (!nameJVM.matches(namePattern) || nameJVM.length > 256)
            throw new Exception(nameJVM + " does not match the required regex: " + namePattern + " or is longer than 256 characters")
          else if (!nameNode.matches(namePattern) || nameNode.length > 256)
            throw new Exception(nameJVM + " does not match the required regex: " + namePattern + " or is longer than 256 characters")
          else {
            println(nameJVM)
            println(nameNode)
          }
        }
      }
    )
  }

  import autoImport._

  override lazy val projectSettings =
    inConfig(Compile)(baseSettings) ++
    Seq(
      assemblyMergeStrategy in assembly := {  // this is so ugly
        case PathList(ps @ _*) if ps.last endsWith "io.netty.versions.properties"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "Log.class"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "LogConfigurationException.class"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "LogFactory.class"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "NoOpLog.class"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "SimpleLog$1.class"  => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith "SimpleLog.class"  => MergeStrategy.first
        case PathList("JS_DEPENDENCIES")  => MergeStrategy.discard
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      lambdaDisableJVM := false,
      lambdaDisableNode := false
    )
}