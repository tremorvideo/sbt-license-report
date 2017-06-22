organization := "com.typesafe.sbt"

name := "sbt-license-report"

sbtPlugin := true

publishMavenStyle := false
//bintrayOrganization := Some("sbt")
//name in bintray := "sbt-license-report"
//bintrayRepository := "sbt-plugin-releases"

publishTo := {
  Some(
    Resolver.url(
      "sbt-plugin-releases",
      new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/")
    )(Resolver.ivyStylePatterns)
  )
  val artifactory = "http://artifactory.service.iad1.consul:8081/artifactory/"
  val (name, url) = if (version.value.contains("-SNAPSHOT"))
    ("Artifactory Realm", artifactory + "libs-snapshot;build.timestamp=" + new java.util.Date().getTime)
  else
    ("Artifactory Realm", artifactory + "libs-release;build.timestamp=" + new java.util.Date().getTime)
  Some(Resolver.url(name, new URL(url)))
}

scalariformSettings

//versionWithGit

//git.baseVersion := "1.0"

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.1"
libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.3.0"

scriptedSettings

scriptedLaunchOpts <+= version apply { v => "-Dproject.version="+v }
