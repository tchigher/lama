import sbt.Keys.libraryDependencies
// Build shared info
ThisBuild / organization := "co.ledger"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.3"
ThisBuild / resolvers += Resolver.sonatypeRepo("releases")
ThisBuild / scalacOptions ++= CompilerFlags.all
ThisBuild / buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, git.gitHeadCommit)
ThisBuild / buildInfoPackage := "buildinfo"
ThisBuild / libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

enablePlugins(BuildInfoPlugin)

lazy val assemblyFolder = file("assembly")
lazy val ignoreFiles    = List("application.conf.sample")

// Runtime
scalaVersion := "2.13.3"
scalacOptions ++= CompilerFlags.all
resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")

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

lazy val sharedSettings = assemblySettings ++ dockerSettings ++ Defaults.itSettings

// Common lama library
lazy val common = (project in file("common"))
  .settings(
    name := "lama-account-manager",
    sharedSettings,
    libraryDependencies ++= Dependencies.lama_common
  )

lazy val accountManager = (project in file("account-manager"))
  .enablePlugins(Fs2Grpc, FlywayPlugin, DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-account-manager",
    sharedSettings,
    // Dependencies
    libraryDependencies ++= Dependencies.account_manager,
    libraryDependencies ++= Dependencies.test,

    // Proto config
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,

    // Flyway credentials to migrate sql scripts
    flywayLocations += "db/migration",
    flywayUrl := "jdbc:postgresql://localhost:5432/lama",
    flywayUser := "lama",
    flywayPassword := "serge",
  ).dependsOn(common)

lazy val bitcoinInterpreter = (project in file("coins/bitcoin/interpreter"))
  .enablePlugins(DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-interpreter",
    sharedSettings,
  )
  .dependsOn(common)

lazy val btcService = (project in file("coins/bitcoin/service"))
  .enablePlugins(Fs2Grpc, DockerPlugin)
  .settings(
    name := "lama-bitcoin-service",
    libraryDependencies ++= Dependencies.bitcoin_service,
    sharedSettings
  )
  .dependsOn(common)

lazy val btcWorker = (project in file("coins/bitcoin/worker"))
  .enablePlugins(DockerPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "lama-bitcoin-worker",
    sharedSettings,
    libraryDependencies ++= (Dependencies.http4s ++ Dependencies.test)
  )
  .dependsOn(common)

