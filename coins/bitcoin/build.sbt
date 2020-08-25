lazy val assemblyFolder = file("assembly")
lazy val ignoreFiles    = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

// Common project and settings
lazy val common = (project in file("common"))
  .enablePlugins(BuildInfoPlugin, DockerPlugin, Fs2Grpc)
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    organization := "co.ledger",
    libraryDependencies ++= Dependencies.common,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit),
    buildInfoPackage := "buildinfo",
    cleanFiles += assemblyFolder,
    test in assembly := {},
    assemblyOutputPath in assembly := assemblyFolder / (name.value + "-" + version.value + ".jar"),
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
        tag = Some(version.value)
      )
    ),
    // User `docker` to build docker image
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("openjdk:14.0.2")
        add(artifact, artifactTargetPath)
        entryPoint("java", "-jar", artifactTargetPath)
      }
    }
  )

// Projects
lazy val worker = (project in file("worker"))
  .settings(
    name := "lama-bitcoin-worker",
    version := "0.0.1-SNAPSHOT"
  )
  .dependsOn(common)

lazy val interpreter = (project in file("interpreter"))
  .settings(
    name := "lama-bitcoin-interpreter",
    version := "0.0.1-SNAPSHOT"
  )
  .dependsOn(common)

lazy val service = (project in file("service"))
  .settings(
    name := "lama-bitcoin-service",
    version := "0.0.1-SNAPSHOT",
    libraryDependencies ++= Dependencies.http4s
  )
  .dependsOn(common)
