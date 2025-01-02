import sbt._
import Keys._

object Dependencies {
  val scala212 = "2.12.20"
  val scala213 = "2.13.15"
  val scala3 = "3.3.4"

  val scalaCompiler = Def.setting {
    val v = if (scalaBinaryVersion.value == "3") scala213 else scalaVersion.value
    "org.scala-lang" % "scala-compiler" % v
  }

  val scalaVerify = "com.eed3si9n.verify" %% "verify" % "1.0.0"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.18.1"
  val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"
  val swovalFiles = "com.swoval" % "file-tree-views" % "2.1.12"
}
