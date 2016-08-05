// Your profile name of the sonatype account. The default is the same with the organization value
sonatypeProfileName := "com.solidys"

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>(your project URL)</url>
  <!-- License of your choice -->
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
  <!-- SCM information. Modify the following URLs -->
    <scm>
      <connection>scm:git:github.com/(your repository URL)</connection>
      <developerConnection>scm:git:git@github.com:(your repository URL)</developerConnection>
      <url>github.com/(your repository url)</url>
    </scm>
  <!-- Developer contact information -->
    <developers>
      <developer>
        <id>(your favorite id)</id>
        <name>(your name)</name>
        <url>(your web page)</url>
      </developer>
    </developers>
}