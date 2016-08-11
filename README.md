sbt-fluidlambda
-----------------

This plugin will automatically create and update either a node.js and a java8 lambda handler

The purpose is to facilitate multi-project sbt builds where some lambdas are targeting the jvm and others node.


plugins.sbt
```
addSbtPlugin("com.solidys" % "sbt-fluidlambda" % "0.1-SNAPSHOT")
```


build.sbt example
```
lambdaExecutionRole := "arn:aws:iam::171493838592:role/lambda_dynamo"
lambdaAwsRegion := "us-west-2"
lambdaHandler := "test.test"
lambdaRuntime := "nodejs4.3"
//lambdaRuntime := "java8"
lambdaBucket := "solidyssbttest"

enablePlugins(ScalaJSPlugin)
enablePlugins(SbtFluidLambda)
```

Here is a simple example, that will compile and work as either a nodejs or java lambda
```
package test
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js
import scala.scalajs.js.JSON
import com.amazonaws.services.lambda.runtime.Context
import java.io.InputStream
import java.io.OutputStream

@JSExport
object test {
  @JSExport
  def handler(event:js.Object, context:js.Object):String = {
    val input = JSON.stringify(event)
    test(input)
  }

  def handler(inputStream:InputStream , outputStream:OutputStream , context:Context):Unit = {
    val input = scala.io.Source.fromInputStream(inputStream).mkString
    outputStream.write(
      test(input).getBytes
    )
  }

  def test(queryText:String) =
    "TEST:  "+queryText+"  :TEST"
}


```

Usage
---
```
    val createLambda = taskKey[Unit]("Creates an amazon lambda for the given project")
    val updateLambda = taskKey[Unit]("Updates the code for a lambda")
    val removeLambda = taskKey[Unit]("Removes the lambda")
    val testLambda = taskKey[Unit]("tests the lambda with the string in lambdaTestEvent")
```

Change function naming example
```
lambdaFunctionName <<= (name, organization, version, lambdaRuntime, lambdaHandler) map {
        (n, o, v, runtime, handler) => {
          (o + "_" + n + "_" + handler + "_" + v+"_" + runtime).replace(".", "-").replace(":","--")
        }
      }
```

It's a quick and dirty implimentation at this point, but basic functionality works currently

Contributions and comments are welcome :)