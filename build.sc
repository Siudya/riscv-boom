// import Mill dependency
import mill._
import scalalib._
import scalafmt._
import $file.`rocket-chip`.common
import $file.`rocket-chip`.dependencies.hardfloat.common
import $file.`rocket-chip`.dependencies.cde.common
import $file.`rocket-chip`.dependencies.diplomacy.common

val defaultVersions = Map(
  "chisel" -> "6.5.0",
  "chisel-plugin" -> "6.5.0",
  "chiseltest" -> "5.0.0",
  "scala" -> "2.13.12",
  "scalatest" -> "3.2.7"
)

def getVersion(dep: String, org: String = "org.chipsalliance", cross: Boolean = false) = {
  val version = sys.env.getOrElse(dep + "Version", defaultVersions(dep))
  if (cross)
    ivy"$org:::$dep:$version"
  else
    ivy"$org::$dep:$version"
}

trait CommonModule extends ScalaModule {
  override def scalaVersion = defaultVersions("scala")
  override def scalacPluginIvyDeps = Agg(getVersion("chisel-plugin", cross = true))
  override def scalacOptions = super.scalacOptions() ++ Agg("-Ymacro-annotations", "-Ytasty-reader")
}

object hardfloat extends Hardfloat
trait Hardfloat
  extends millbuild.`rocket-chip`.dependencies.hardfloat.common.HardfloatModule {
    def scalaVersion: T[String] = T(defaultVersions("scala"))
    override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "hardfloat" / "hardfloat"
    def chiselModule = None
    def chiselPluginJar = None
    def chiselIvy = Some(getVersion("chisel"))
    def chiselPluginIvy = Some(getVersion("chisel-plugin", cross = true))
}

object diplomacy extends Diplomacy
trait Diplomacy
  extends millbuild.`rocket-chip`.dependencies.diplomacy.common.DiplomacyModule
  with ScalaModule {
    override def scalaVersion: T[String] = T(defaultVersions("scala"))
    override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "diplomacy" / "diplomacy"
    def chiselModule = None
    def chiselPluginJar = None
    def chiselIvy = Some(getVersion("chisel"))
    def chiselPluginIvy = Some(getVersion("chisel-plugin", cross = true))
    def cdeModule = cde
    def sourcecodeIvy = ivy"com.lihaoyi::sourcecode:0.3.1"
}

object macros extends Macros
trait Macros
  extends millbuild.`rocket-chip`.common.MacrosModule
  with SbtModule {
    def scalaVersion: T[String] = T(defaultVersions("scala"))
    def scalaReflectIvy = ivy"org.scala-lang:scala-reflect:${defaultVersions("scala")}"
}

object cde extends CDE
trait CDE
  extends millbuild.`rocket-chip`.dependencies.cde.common.CDEModule
  with ScalaModule {
    def scalaVersion: T[String] = T(defaultVersions("scala"))
    override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "cde" / "cde"
}

object rocketchip extends RocketChip

trait RocketChip
  extends millbuild.`rocket-chip`.common.RocketChipModule
    with SbtModule {
  def scalaVersion: T[String] = T(defaultVersions("scala"))
  override def millSourcePath = os.pwd / "rocket-chip"
  def chiselModule = None
  def chiselPluginJar = None
  def chiselIvy = Some(getVersion("chisel"))
  def chiselPluginIvy = Some(getVersion("chisel-plugin", cross = true))
  def macrosModule = macros
  def hardfloatModule = hardfloat
  def cdeModule = cde
  def diplomacyModule = diplomacy
  def diplomacyIvy = None
  def mainargsIvy = ivy"com.lihaoyi::mainargs:0.5.0"
  def json4sJacksonIvy = ivy"org.json4s::json4s-jackson:4.0.5"
}


object cdt extends SbtModule with ScalafmtModule with CommonModule {
  override def millSourcePath = os.pwd / "cdt"
  override def moduleDeps = super.moduleDeps ++ Seq(cde)
  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chisel"),
    getVersion("chiseltest", "edu.berkeley.cs"),
  )
}

object boom extends SbtModule with CommonModule {
  override def millSourcePath = os.pwd
  override def moduleDeps = super.moduleDeps ++ Seq(cdt, cde, rocketchip)
  override def ivyDeps = super.ivyDeps() ++ Agg(
    getVersion("chisel"),
    getVersion("chiseltest", "edu.berkeley.cs"),
  )
}
