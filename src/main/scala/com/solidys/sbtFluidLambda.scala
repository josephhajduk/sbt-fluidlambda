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

object SbtFluidLambda extends AutoPlugin {
  override def requires = org.scalajs.sbtplugin.ScalaJSPlugin && sbtassembly.AssemblyPlugin

  // from scala.js plugin
  val fullOptJS = org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.fullOptJS
  val packageJSDependencies = org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.packageJSDependencies
  val scalaJSOutputWrapper = org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.scalaJSOutputWrapper

  // from assembly
  val assembly = sbtassembly.AssemblyKeys.assembly
  val assemblyMergeStrategy = sbtassembly.AssemblyKeys.assemblyMergeStrategy

  object autoImport {
    val createLambda = taskKey[Unit]("Creates an amazon lambda for the given project")
    val updateLambda = taskKey[Unit]("Updates the code for a lambda if the definition hasn't changed")
    val removeLambda = taskKey[Unit]("Removes the lambda")
    val preparePayload = taskKey[File]("Creates the source code payload")
    val prepareJVMPayload = taskKey[File]("Creates the JVM source code payload")
    val prepareNodePayload = taskKey[File]("Creates the JVM source code payload")

    val lambdaFunctionName = taskKey[String]("Sets the function name for the java lambda")

    val lambdaRuntime = settingKey[String]("runtime for lambda,  node or jvm")
    val lambdaExecutionRole = settingKey[String]("lambda arn execution role")
    val lambdaAwsRegion = settingKey[String]("AWS region to deploy to")
    val lambdaHandler = settingKey[String]("handler object")

    val dynPayload = Def.taskDyn {
      if (lambdaRuntime.value == "nodejs4.3")
        prepareNodePayload
      else
        prepareJVMPayload
    }

    lazy val baseSettings: Seq[Def.Setting[_]] = Seq(

      scalaJSOutputWrapper := ("",
        """
          |exports.handler = function(event, context) {
          |        try {
          |            var msg = HANDLER().handler();
          |            context.done(null, msg);
          |        } catch (err) {
          |            context.done(err.toString(), null);
          |        }
          |};
        """.stripMargin.replace("HANDLER",lambdaHandler.value)),


      lambdaFunctionName <<= (name, organization, version, lambdaRuntime, lambdaHandler) map {
        (n, o, v, runtime, handler) => {
          (o + "_" + n + "_" + handler + "_" + v+"_" + runtime).replace(".", "-").replace(":","--")
        }
      },

      preparePayload := dynPayload.value,

      prepareNodePayload in Compile <<= (fullOptJS in Compile, packageJSDependencies in Compile, target) map {
        (jsFile, depsFile, tf) =>

          println("NODEPAYLOAD")

          val zipFile = tf / "lambda.zip"
          val inputs: Seq[(File, String)] = Seq((jsFile.data, "index.js"))

          IO.zip(inputs, zipFile)

          zipFile
      },

      createLambda in Compile <<= (
        preparePayload in Compile,
        lambdaFunctionName,
        lambdaRuntime,
        lambdaExecutionRole,
        lambdaAwsRegion,
        lambdaHandler) map {
        (payload, funcName, runtime, execRole, region, handler) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          println(funcName)

          if (runtime == "nodejs4.3") {
            val nodeFS = new FileInputStream(payload)
            val nodeBuffer = ByteBuffer.wrap(Stream.continually(nodeFS.read).takeWhile(_ != -1).map(_.toByte).toArray)

            client.createFunction(new CreateFunctionRequest()
              .withFunctionName(funcName)
              .withHandler("index.handler")
              .withRuntime(model.Runtime.Nodejs43)
              .withRole(execRole)
              .withCode(new FunctionCode().withZipFile(nodeBuffer))
            )

          } else {

          }
        }
      }
    )
  }

  import autoImport._

  override lazy val projectSettings =
    inConfig(Compile)(baseSettings) ++
      Seq(
        assemblyMergeStrategy in assembly := {
          // this is so ugly
          case PathList(ps@_*) if ps.last endsWith "io.netty.versions.properties" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "Log.class" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "LogConfigurationException.class" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "LogFactory.class" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "NoOpLog.class" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "SimpleLog$1.class" => MergeStrategy.first
          case PathList(ps@_*) if ps.last endsWith "SimpleLog.class" => MergeStrategy.first
          case PathList("JS_DEPENDENCIES") => MergeStrategy.discard
          case x =>
            val oldStrategy = (assemblyMergeStrategy in assembly).value
            oldStrategy(x)
        },
        lambdaRuntime := "node43"
      )
}