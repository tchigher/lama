// Enable common plugins
enablePlugins(BuildInfoPlugin)

lazy val assemblyFolder = file("assembly")
lazy val ignoreFiles    = List("application.conf.sample")

// Runtime
scalaVersion in ThisBuild := "2.13.3"
scalacOptions in ThisBuild ++= CompilerFlags.all
resolvers in ThisBuild += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

lazy val commonSettings = Seq(
  organization := "co.ledger",
  version := "0.0.1-SNAPSHOT",
  libraryDependencies ++= Dependencies.common,
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit),
  buildInfoPackage := "buildinfo"
)

lazy val assemblySettings = Seq(
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
  }
)

lazy val dockerSettings = Seq(
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

lazy val projectSettings =
  commonSettings ++ assemblySettings ++ dockerSettings ++ Defaults.itSettings

// Common project to share code
lazy val common = (project in file("common"))
  .settings(commonSettings)

// Projects
lazy val accountManager = (project in file("account-manager"))
  .enablePlugins(Fs2Grpc, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    projectSettings,
    name := "lama-account-manager",
    libraryDependencies ++= (
      Dependencies.postgres ++
        Dependencies.redis ++
        Dependencies.test
    ),
    // Flyway credentials to migrate sql scripts
    flywayLocations := Seq("db/migration"),
    flywayUrl := "jdbc:postgresql://localhost:5432/lama",
    flywayUser := "lama",
    flywayPassword := "serge",
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage
  )
  .dependsOn(common)

lazy val bitcoinWorker = (project in file("coins/bitcoin/worker"))
  .enablePlugins(DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    projectSettings,
    name := "lama-bitcoin-worker",
    libraryDependencies ++= (Dependencies.http4s ++ Dependencies.test)
  )
  .dependsOn(common)

lazy val bitcoinInterpreter = (project in file("coins/bitcoin/interpreter"))
  .enablePlugins(DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    projectSettings,
    name := "lama-bitcoin-interpreter"
  )
  .dependsOn(common)

//lazy val bitcoinService = (project in file("coins/bitcoin/service"))
//  .enablePlugins(Fs2Grpc, DockerPlugin)
//  .settings(
//    name := "lama-bitcoin-service",
//    version := "0.0.1-SNAPSHOT",
//    libraryDependencies ++= (Dependencies.http4s ++ Dependencies.test)
//  )
//  .dependsOn(common)
