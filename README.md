sbt-fluidlambda
-----------------

This plugin will automatically create and update either or oth a node.js and a java8 lambda handler

```
lambdaExecutionRole := "arn:aws:iam::171453408592:role/lambda_dynamo"
lambdaAwsRegion := "us-west-2"
lambdaHandler := "test.test"

enablePlugins(ScalaJSPlugin)
enablePlugins(SbtFluidLambda)
```

It's not done yet