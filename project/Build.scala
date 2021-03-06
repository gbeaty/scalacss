import sbt._
import Keys._
import com.typesafe.sbt.pgp.PgpKeys._
import org.scalajs.sbtplugin.ScalaJSPlugin
import ScalaJSPlugin._
import ScalaJSPlugin.autoImport._
import Dialect._
import Typical.{settings => _, _}

object ScalaCSS extends Build {

  val Scala211 = "2.11.7"

  val commonSettings: CDS =
    CDS.all(
      _.settings(
        organization       := "com.github.japgolly.scalacss",
        version            := "0.4.1-SNAPSHOT",
        homepage           := Some(url("https://github.com/japgolly/scalacss")),
        licenses           += ("Apache-2.0", url("http://opensource.org/licenses/Apache-2.0")),
        scalaVersion       := Scala211,
        // crossScalaVersions := Seq("2.10.5", Scala211),
        scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature",
                                "-language:postfixOps", "-language:implicitConversions",
                                "-language:higherKinds", "-language:existentials"),
        updateOptions      := updateOptions.value.withCachedResolution(true))
      .configure(addCommandAliases(
        "cj"  -> "project core-jvm",
        "cjs" -> "project core-js",
        "qc"  -> "~core-jvm/compile",
        "qtc" -> "~core-jvm/test:compile",
        "qt"  -> "~core-jvm/test"
      ))
    ) :+ Typical.settings("scalacss")

  object scalaz {
    private def m(n: String) = Library("org.scalaz", "scalaz-"+n, "7.2.0").myJsFork("scalaz") //.jsVersion(_+"-2")
    val core       = m("core")
    val effect     = m("effect") > core
    val concurrent = m("concurrent") > effect
  }
  object nyaya {
    private def m(n: String) = Library("com.github.japgolly.nyaya", "nyaya-"+n, "0.7.0")
    val core = m("core")
    val test = m("test")
  }
  object react {
    private def m(n: String) = "com.github.japgolly.scalajs-react" %%%! n % "0.10.3"
    val core        = m("core")
    val test        = m("test")
    val extra       = m("extra")
    val extScalaz72 = m("ext-scalaz72")
  }


  // ==============================================================================================
  override def rootProject = Some(root)

  lazy val root =
    Project("root", file("."))
      .configure(commonSettings.rootS, preventPublication)
      .aggregate(core, extReact, extScalatags, bench)
      .settings(
        scalaJSUseRhino in Global := false)

  lazy val (core, coreJvm, coreJs) =
    crossDialectProject("core", commonSettings
      .configure(definesMacros, utestSettings()) //, Gen.attrAliases)
      .addLibs(scalaz.core, nyaya.test % Test)
      .jj(_ => initialCommands := "scalacss._")
    )

  lazy val (extScalatags, extScalatagsJvm, extScalatagsJs) =
    crossDialectProject("ext-scalatags", commonSettings
      .configure(utestSettings())
      .dependsOn(coreJvm, coreJs)
      .addLibs(Library("com.lihaoyi", "scalatags", "0.5.0"))
    )

  lazy val extReact =
    Project("ext-react", file("ext-react"))
      .enablePlugins(ScalaJSPlugin)
      .configure(commonSettings.jsS, utestSettings().jsS)
      .dependsOn(coreJs)
      .settings(
        libraryDependencies ++= Seq(react.core, react.test % "test"),
        jsDependencies += "org.webjars.npm" % "react"     % "0.14.0" % "test" / "react-with-addons.js" commonJSName "React"    minified "react-with-addons.min.js",
        jsDependencies += "org.webjars.npm" % "react-dom" % "0.14.0" % "test" / "react-dom.js"         commonJSName "ReactDOM" minified "react-dom.min.js" dependsOn "react-with-addons.js")

  // ==============================================================================================
  private def benchModule(name: String, dir: File => File) =
    Project(name, dir(file("bench")))
      .configure(commonSettings.rootS, preventPublication)

  private def benchReactModule(name: String, dir: File => File) =
    benchModule("bench-" + name, dir)
      .enablePlugins(ScalaJSPlugin)
      .settings(
        scalaSource in Compile := baseDirectory.value / "src",
        libraryDependencies ++= Seq(react.extra, react.extScalaz72))

  val intfmt = java.text.NumberFormat.getIntegerInstance

  val cmpJsSizeFast = TaskKey[Unit]("cmpJsSizeFast", "Compare JS sizes (using fastOptJS).")
  val cmpJsSizeFull = TaskKey[Unit]("cmpJsSizeFull", "Compare JS sizes (using fullOptJS).")
  def cmpJsSize(js: TaskKey[Attributed[sbt.File]]) = Def.task {
    val a = (js in Compile in benchReactWithout).value.data
    val b = (js in Compile in benchReactWith   ).value.data
    val x = a.length()
    val y = b.length()
    val d = y - x
    printf(
      """
        | Without: %10s
        |    With: %10s
        |Increase: %10s (+%.0f%%)
        |
        |""".stripMargin,
      intfmt format x,
      intfmt format y,
      intfmt format d, d.toDouble / x * 100)
  }

  lazy val bench =
    benchModule("bench", identity)
      .aggregate(benchReactWith, benchReactWithout)
      .settings(
        cmpJsSizeFast := cmpJsSize(fastOptJS).value,
        cmpJsSizeFull := cmpJsSize(fullOptJS).value
      )
      .configure(addCommandAliases(
        "cmpJsSize" -> ";cmpJsSizeFast ;cmpJsSizeFull"
      ))

  lazy val benchReactWithout =
    benchReactModule("react-without", _ / "react-without")

  lazy val benchReactWith =
    benchReactModule("react-with", _ / "react-with")
      .dependsOn(extReact)
}
