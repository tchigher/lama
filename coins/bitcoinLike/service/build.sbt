// Enable plugins
enablePlugins(DockerPlugin, Fs2Grpc)

lazy val assemblyFolder = file("assembly")
lazy val ignoreFiles    = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

// Dependencies
libraryDependencies ++= Dependencies.all

// Projects
lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin, Fs2Grpc)
  .settings(
    organization := "co.ledger",
    name := "lama-bitcoin-service",
    version := "0.0.1-SNAPSHOT",
    cleanFiles += assemblyFolder,
    test in assembly := {},
    assemblyOutputPath in assembly := assemblyFolder / (name.value + "-v" + version.value + ".jar"),
    // Remove resources files from the JAR (they will be copied to an external folder)
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", _) => MergeStrategy.discard
      case PathList("BUILD")       => MergeStrategy.discard
      case path =>
        if (ignoreFiles.contains(path))
          MergeStrategy.discard
        else
          (assemblyMergeStrategy in assembly).value(path)
    },
    imageNames in docker := Seq(
      // Sets the latest tag
      ImageName(s"ledgerhq/${name.value}:latest"),
      // Sets a name with a tag that contains the project version
      ImageName(
        namespace = Some("ledgerhq"),
        repository = name.value,
        tag = Some("v" + version.value)
      )
    ),
    // User `docker` to build docker image
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:8-jre")
        add(artifact, artifactTargetPath)
        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  )
