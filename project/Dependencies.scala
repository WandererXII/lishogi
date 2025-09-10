import sbt.Keys._
import sbt._

object Dependencies {

  object Resolvers {

    val lilaMaven =
      "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"

    val commons = Seq(lilaMaven)
  }

  val shogi     = "io.github.wandererxii" %% "scalashogi"           % "12.2.1"
  val hasher    = "com.roundeights"       %% "hasher"               % "1.2.1"
  val jodaTime  = "joda-time"              % "joda-time"            % "2.14.0"
  val maxmind   = "com.sanoma.cda"        %% "maxmind-geoip2-scala" % "1.3.1-THIB"
  val scrimage  = "com.sksamuel.scrimage"  % "scrimage-core"        % "4.3.0"
  val scaffeine = "com.github.blemale"    %% "scaffeine"            % "5.3.0" % "compile"
  val googleOAuth = "com.google.auth"    % "google-auth-library-oauth2-http" % "1.33.1"
  val galimatias  = "io.mola.galimatias" % "galimatias"                      % "0.2.2-NF"
  val scalatags   = "com.lihaoyi"       %% "scalatags"                       % "0.13.1"
  val lettuce     = "io.lettuce"         % "lettuce-core"                    % "6.8.1.RELEASE"
  val autoconfig  = "io.methvin.play"   %% "autoconfig-macros"               % "0.3.2" % "provided"
  val uaparser    = "org.uaparser"      %% "uap-scala"                       % "0.20.0"
  val apacheText  = "org.apache.commons" % "commons-text"                    % "1.14.0"

  object play {
    import _root_.play.sbt.PlayImport

    val core      = PlayImport.playCore
    val ws        = PlayImport.ws
    val json      = "com.typesafe.play" %% "play-json"      % "2.10.7"
    val jsonJoda  = "com.typesafe.play" %% "play-json-joda" % "2.10.7"
    val jodaForms = PlayImport.jodaForms
    val specs2    = PlayImport.specs2    % Test
  }

  object akka {
    val version = "2.6.21"

    val actor   = "com.typesafe.akka" %% "akka-actor"       % version
    val typed   = "com.typesafe.akka" %% "akka-actor-typed" % version
    val stream  = "com.typesafe.akka" %% "akka-stream"      % version
    val slf4j   = "com.typesafe.akka" %% "akka-slf4j"       % version
    val testkit = "com.typesafe.akka" %% "akka-testkit"     % version % Test
  }

  object cats {
    val version = "2.13.0"

    val core      = "org.typelevel" %% "cats-core"      % version
    val alleycats = "org.typelevel" %% "alleycats-core" % version
  }

  object macwire {
    val version = "2.6.7"

    val macros = "com.softwaremill.macwire" %% "macros" % version % "provided"
    val util   = "com.softwaremill.macwire" %% "util"   % version % "provided"
  }

  object reactivemongo {
    val version = "1.1.0-RC17"

    val driver = "org.reactivemongo" %% "reactivemongo"            % version
    val stream = "org.reactivemongo" %% "reactivemongo-akkastream" % version
  }

  object kamon {
    val version = "2.7.5"

    val core       = "io.kamon" %% "kamon-core"           % version
    val influxdb   = "io.kamon" %% "kamon-influxdb"       % version
    val metrics    = "io.kamon" %% "kamon-system-metrics" % version
    val prometheus = "io.kamon" %% "kamon-prometheus"     % version
  }

  object flexmark {
    val version = "0.64.8"

    val bundle =
      ("com.vladsch.flexmark" % "flexmark" % version) ::
        List("ext-tables", "ext-autolink", "ext-gfm-strikethrough").map { ext =>
          "com.vladsch.flexmark" % s"flexmark-$ext" % version
        }
  }

}
