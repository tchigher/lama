import sbt.librarymanagement.{DependencyBuilders, LibraryManagementSyntax, ModuleID}

object Dependencies extends DependencyBuilders with LibraryManagementSyntax {

  val http4sVersion = "0.21.0"
  val http4s: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-blaze-server"       % http4sVersion,
    "org.http4s" %% "http4s-blaze-client"       % http4sVersion,
    "org.http4s" %% "http4s-circe"              % http4sVersion,
    "org.http4s" %% "http4s-dsl"                % http4sVersion,
    "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
  )

  val H2Version     = "1.4.200"
  val flywayVersion = "6.5.0"
  val doobieVersion = "0.8.8"
  val postgres: Seq[ModuleID] = Seq(
    "com.h2database" % "h2"              % H2Version,
    "org.flywaydb"   % "flyway-core"     % flywayVersion,
    "org.tpolecat"  %% "doobie-core"     % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat"  %% "doobie-h2"       % doobieVersion
  )

  val circeVersion = "0.13.0"
  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion
  )

  val pureconfigVersion = "0.13.0"
  val logbackVersion    = "1.2.3"
  val fs2Version        = "2.4.2"
  val utilities: Seq[ModuleID] = Seq(
    "co.fs2"                %% "fs2-core"          % fs2Version,
    "ch.qos.logback"         % "logback-classic"   % logbackVersion,
    "com.github.pureconfig" %% "pureconfig"        % pureconfigVersion,
    "com.github.pureconfig" %% "pureconfig-cats"   % pureconfigVersion,
    "io.grpc"                % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion
  )

  val fs2RabbitVersion = "2.1.1"
  val rabbit: Seq[ModuleID] = Seq(
    "dev.profunktor" %% "fs2-rabbit" % fs2RabbitVersion
  )

  val scalaRedisVersion = "3.30"
  val redis: Seq[ModuleID] = Seq(
    "net.debasishg" %% "redisclient" % scalaRedisVersion
  )

  val scalaTestVersion     = "3.2.0"
  val scalaTestPlusVersion = "3.2.0.0"
  val scalaCheckVersion    = "1.14.3"
  val otjPgEmbeddedVersion = "0.13.3"
  val embeddedRedisVersion = "0.7.3"
  val test: Seq[ModuleID] = Seq(
    "org.scalatest"           %% "scalatest"        % scalaTestVersion     % "it, test",
    "org.scalacheck"          %% "scalacheck"       % scalaCheckVersion    % "it, test",
    "org.scalatestplus"       %% "scalacheck-1-14"  % scalaTestPlusVersion % "it, test",
    "org.tpolecat"            %% "doobie-scalatest" % doobieVersion        % "it, test",
    "com.opentable.components" % "otj-pg-embedded"  % otjPgEmbeddedVersion % Test,
    "it.ozimov"                % "embedded-redis"   % embeddedRedisVersion % Test
  )

  val common: Seq[ModuleID] = circe ++ rabbit ++ utilities

}
