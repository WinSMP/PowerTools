import Dependencies._

lazy val mainScalaClass = "org.winlogon.powertools.PowerToolsPlugin"
lazy val scalaVer = "3.3.5"

ThisBuild / scalaVersion     := scalaVer
ThisBuild / version          := "0.3.0-SNAPSHOT"
ThisBuild / organization     := "org.winlogon"
ThisBuild / organizationName := "winlogon"
Compile / mainClass := Some(mainScalaClass)

// GitHub CI
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))
ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq(scalaVer)

lazy val root = (project in file("."))
  .settings(
    name := "powertools",
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false),
  )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assembly / mainClass := Some(mainScalaClass)

libraryDependencies ++= Seq(
  "io.papermc.paper" % "paper-api" % "1.21.4-R0.1-SNAPSHOT" % Provided,
  "net.kyori" % "adventure-text-minimessage" % "4.18.0" % Provided,
  "net.kyori" % "adventure-platform-api" % "4.3.4" % Provided,
  "dev.jorel" % "commandapi-bukkit-core" % "9.7.0" % Provided,
)

resolvers ++= Seq(
  "papermc-repo" at "https://repo.papermc.io/repository/maven-public/",
  "codemc" at "https://repo.codemc.org/repository/maven-public/",
)
