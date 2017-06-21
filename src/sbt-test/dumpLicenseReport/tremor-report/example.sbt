name := "example"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.5.4"
libraryDependencies += "junit"                      % "junit"            % "4.12"  % "test"

excludeDependencies += SbtExclusionRule(organization = "org.scala-lang")

TaskKey[Unit]("check") := {
  val contents = sbt.IO.read(target.value / "license-reports" / "example-licenses.md")
  if (!contents.contains("Open Source Name | Version | License | Link to License | Dual-Licensed? | Link to Source | Products | Function Type | Interaction | Modified | Distribution (Downloadable, Internally Used, SaaS) | Compiled Together"))
    sys.error("Expected report to contain Tremor report headers: " + contents)
  if (!contents.contains("jackson-core | 2.5.4 | Apache | [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)"))
    sys.error("Expected report to contain jackson-databind with Apache license: " + contents)
  if (!contents.contains("jackson-databind"))
    sys.error("Expected report to contain jackson-databind: " + contents)
  if (!contents.contains("junit | 4.12 | unrecognized | [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html)"))
    sys.error("Expected report to contain junit with EPL license: " + contents)
  // Test whether exclusions are included.
  if (contents.contains("scala-library"))
    sys.error("Expected report to NOT contain scala-library: " + contents)

}
