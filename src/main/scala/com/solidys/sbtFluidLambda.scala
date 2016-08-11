package com.solidys

import java.io.FileInputStream
import java.nio.ByteBuffer

import sbt._
import Keys._
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.{AWSLambdaClient, model => lambdamodel}
import com.amazonaws.services.s3.{AmazonS3Client, model => s3model}
import com.amazonaws.services.lambda.model._
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
    val updateLambda = taskKey[Unit]("Updates the code for a lambda")
    val removeLambda = taskKey[Unit]("Removes the lambda")
    val testLambda = taskKey[Unit]("tests the lambda with the string in lambdaTestEvent")

    val preparePayload = taskKey[File]("Creates the source code payload")
    val prepareJVMPayload = taskKey[File]("Creates the JVM source code payload")
    val prepareNodePayload = taskKey[File]("Creates the Node source code payload")
    val prepareOtherPayload = taskKey[File]("Create payload for unsupported runtime")

    val lambdaTestEvent = taskKey[String]("The test json sent during the testLambda task")
    val lambdaFunctionName = taskKey[String]("Sets the function name for the java lambda")
    val lambdaRuntime = settingKey[String]("runtime for lambda,  node or jvm")
    val lambdaExecutionRole = settingKey[String]("lambda arn execution role")
    val lambdaAwsRegion = settingKey[String]("AWS region to deploy to")
    val lambdaHandler = settingKey[String]("handler object")
    val lambdaBucket = settingKey[String]("bucket to store lambda code")

    val dynPayload = Def.taskDyn {
      if (lambdaRuntime.value == "nodejs4.3")
        prepareNodePayload
      else if (lambdaRuntime.value == "java8")
        prepareJVMPayload
      else
        prepareOtherPayload
    }

    lazy val baseSettings: Seq[Def.Setting[_]] = Seq(

      scalaJSOutputWrapper := ("",
        """
          |exports.handler = function(event, context) {
          |        try {
          |            var msg = HANDLER().handler(event, context);
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

      prepareJVMPayload in Compile <<= (assembly) map {
        (asm) =>
          asm
      },

      prepareNodePayload in Compile <<= (fullOptJS in Compile, packageJSDependencies in Compile, target) map {
        (jsFile, depsFile, tf) =>

          val zipFile = tf / "lambda.zip"
          val inputs: Seq[(File, String)] = Seq((jsFile.data, "index.js"))

          IO.zip(inputs, zipFile)

          zipFile
      },

      testLambda in Compile <<= (
        lambdaFunctionName,
        lambdaAwsRegion,
        lambdaTestEvent
        ) map {
        (funcName,region,testEvent) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          println("testEvent: "+testEvent)

          val ir = client.invoke(new InvokeRequest()
            .withFunctionName(funcName)
            .withInvocationType(InvocationType.RequestResponse)
            .withLogType(LogType.None)
            .withPayload(testEvent)
          )

          println("result: "+ (new String(ir.getPayload.array())))
        }
      },

      removeLambda in Compile <<= (
        lambdaFunctionName,
        lambdaRuntime,
        lambdaAwsRegion,
        lambdaBucket
        ) map {
        (funcName,runtime,region,bucket) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          client.deleteFunction(new DeleteFunctionRequest()
            .withFunctionName(funcName)
          )
        }
      },

      updateLambda in Compile <<= (
        preparePayload in Compile,
        lambdaFunctionName,
        lambdaRuntime,
        lambdaAwsRegion,
        lambdaBucket
        ) map {
        (payload, funcName, runtime, region, bucket) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          if (runtime == "nodejs4.3") {
            val nodeFS = new FileInputStream(payload)
            val nodeBuffer = ByteBuffer.wrap(Stream.continually(nodeFS.read).takeWhile(_ != -1).map(_.toByte).toArray)

            client.updateFunctionCode( new UpdateFunctionCodeRequest()
              .withFunctionName(funcName)
              .withZipFile(nodeBuffer)
            )

          } else if (runtime == "java8")  {

            val s3client = new AmazonS3Client()
            s3client.setRegion(Region.getRegion(Regions.fromName(region)))
            s3client.putObject(bucket,funcName+"/"+payload.name, payload)

            client.updateFunctionCode( new UpdateFunctionCodeRequest()
              .withFunctionName(funcName)
              .withS3Bucket(bucket)
              .withS3Key(funcName+"/"+payload.name)
            )
          }

        }
      },

      createLambda in Compile <<= (
        preparePayload in Compile,
        lambdaFunctionName,
        lambdaRuntime,
        lambdaExecutionRole,
        lambdaAwsRegion,
        lambdaHandler,
        lambdaBucket) map {
        (payload, funcName, runtime, execRole, region, handler, bucket) => {
          val client = new AWSLambdaClient()
          client.setRegion(Region.getRegion(Regions.fromName(region)))

          println(funcName)

          if (runtime == "nodejs4.3") {
            val nodeFS = new FileInputStream(payload)
            val nodeBuffer = ByteBuffer.wrap(Stream.continually(nodeFS.read).takeWhile(_ != -1).map(_.toByte).toArray)

            client.createFunction(new CreateFunctionRequest()
              .withFunctionName(funcName)
              .withHandler("index.handler")
              .withRuntime(lambdamodel.Runtime.Nodejs43)
              .withRole(execRole)
              .withCode(new FunctionCode().withZipFile(nodeBuffer))
            )

          } else if (runtime == "java8")  {

            val s3client = new AmazonS3Client()
            s3client.setRegion(Region.getRegion(Regions.fromName(region)))
            s3client.putObject(bucket,funcName+"/"+payload.name, payload)

            val fc = new FunctionCode()
            fc.setS3Bucket(bucket)
            fc.setS3Key(funcName+"/"+payload.name)

            client.createFunction(new CreateFunctionRequest()
              .withFunctionName(funcName)
              .withHandler(handler+"::handler")
              .withRuntime(lambdamodel.Runtime.Java8)
              .withRole(execRole)
              .withCode(fc)
            )
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
          // TODO: this is so ugly,  should we put it in for everybody or just the JS_DEPENDENCIES?
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
        lambdaRuntime := "nodejs4.3",
        lambdaTestEvent := """{"key3": "value3","key2": "value2","key1": "value1"}"""
      )
}