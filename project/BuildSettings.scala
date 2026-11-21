import sbt.Keys._
import sbt._

object BuildSettings {

  import Dependencies._

  val globalLilaVersion  = "3.0"
  val globalOrganization = "org.lishogi"
  val globalScalaVersion = "2.13.18"

  def buildSettings =
    Defaults.coreDefaultSettings ++ Seq(
      version      := globalLilaVersion,
      organization := globalOrganization,
      scalaVersion := globalScalaVersion,
      resolvers ++= Dependencies.Resolvers.commons,
      scalacOptions ++= compilerOptions,
      Compile / doc / sources                := Seq.empty,
      Compile / packageDoc / publishArtifact := false,
      Compile / packageSrc / publishArtifact := false,
      Compile / run / fork                   := true,
      Compile / run / javaOptions ++= Seq("-Xms64m", "-Xmx256m"),
    )

  def baseLibs: Seq[ModuleID] = Seq(
    akka.actor,
    akka.typed,
    akka.stream,
    akka.slf4j,
    play.core,
    play.ws,
    play.json,
    play.specs2,
    reactivemongo.driver,
    reactivemongo.stream,
    cats.core,
    cats.alleycats,
    macwire.macros,
    macwire.util,
    jodaTime,
    commonsText,
    shogi,
    scaffeine,
    autoconfig,
  )

  def extraLibs(name: String) =
    name match {
      case "api"      => Seq(hasher, galimatias)
      case "common"   => Seq(hasher, play.jodaForms, scalatags, kamon.core)
      case "i18n"     => Seq(scalatags)
      case "memo"     => Seq(akka.testkit)
      case "oauth"    => Seq(galimatias, hasher)
      case "push"     => Seq(googleOAuth)
      case "security" => Seq(hasher, geoip, uaparser, scalatags)
      case "socket"   => Seq(lettuce)
      case "storm"    => Seq(hasher)
      case "user"     => Seq(hasher)
      case "article"  => flexmark.bundle
      case _          => Seq.empty
    }

  def rootLibs = baseLibs ++ Seq(scalatags, kamon.influxdb, kamon.metrics, kamon.prometheus)

  def module(
      name: String,
      deps: Seq[sbt.ClasspathDep[sbt.ProjectReference]],
  ) =
    Project(
      name,
      file("modules/" + name),
    ).dependsOn(deps: _*)
      .settings(
        libraryDependencies ++= baseLibs ++ extraLibs(name),
        buildSettings,
        srcMain,
      )

  val srcMain = Seq(
    Compile / scalaSource := (Compile / sourceDirectory).value,
    Test / scalaSource    := (Test / sourceDirectory).value,
  )

  val compilerOptions = Seq(
    "-encoding",
    "utf-8",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-language:postfixOps",
    // all lints, except those that are mostly noise here
    "-Xlint:_,-strict-unsealed-patmat",
    "-Wunused", // all unused checks, incl. params, patvars and unneeded @nowarn
    "-Wdead-code",
    "-Wextra-implicit",
    "-Wvalue-discard",
    "-Wmultiarg-infix",
    "-Wmacros:after", // check unused symbols against macro expansions (macwire)
    "-Werror",
  )

}
