import sbt.librarymanagement.{DependencyBuilders, LibraryManagementSyntax, ModuleID}

object Dependencies extends DependencyBuilders with LibraryManagementSyntax {

  val circeVersion = "0.13.0"
  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion
  )

  val H2Version     = "1.4.200"
  val flywayVersion = "6.5.0"
  val doobieVersion = "0.8.8"
  val db: Seq[ModuleID] = Seq(
    "com.h2database" % "h2"              % H2Version,
    "org.flywaydb"   % "flyway-core"     % flywayVersion,
    "org.tpolecat"  %% "doobie-core"     % doobieVersion,
    "org.tpolecat"  %% "doobie-postgres" % doobieVersion,
    "org.tpolecat"  %% "doobie-hikari"   % doobieVersion,
    "org.tpolecat"  %% "doobie-h2"       % doobieVersion
  )

  val pureconfigVersion = "0.13.0"
  val logbackVersion    = "1.2.3"
  val fs2Version        = "2.4.2"
  val fs2RabbitVersion  = "2.1.1"
  val utilities: Seq[ModuleID] = Seq(
    "co.fs2"                %% "fs2-core"          % fs2Version,
    "dev.profunktor"        %% "fs2-rabbit"        % fs2RabbitVersion,
    "ch.qos.logback"         % "logback-classic"   % logbackVersion,
    "com.github.pureconfig" %% "pureconfig"        % pureconfigVersion,
    "com.github.pureconfig" %% "pureconfig-cats"   % pureconfigVersion,
    "io.grpc"                % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion
  )

  val scalaTestVersion     = "3.2.0"
  val scalaTestPlusVersion = "3.2.0.0"
  val scalaCheckVersion    = "1.14.3"
  val test: Seq[ModuleID] = Seq(
    "org.scalatest"     %% "scalatest"        % scalaTestVersion     % Test,
    "org.scalacheck"    %% "scalacheck"       % scalaCheckVersion    % Test,
    "org.scalatestplus" %% "scalacheck-1-14"  % scalaTestPlusVersion % Test,
    "org.tpolecat"      %% "doobie-scalatest" % doobieVersion        % Test
  )

  val all: Seq[ModuleID] = circe ++ db ++ utilities ++ test

}
