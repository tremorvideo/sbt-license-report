package com.typesafe.sbt
package license

import sbt._
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.IvyNode

import scala.xml.Elem
import scalaj.http.{ Http, HttpResponse }

case class DepModuleInfo(organization: String, name: String, version: String, url: String) {
  override def toString = s"${organization} # ${name} # ${version}"
}
case class DepLicense(module: DepModuleInfo, license: LicenseInfo, configs: Set[String]) {
  override def toString = s"$module on $license in ${configs.mkString("(", ",", ")")}"
}

case class LicenseReport(licenses: Seq[DepLicense], orig: ResolveReport) {
  override def toString = s"""|## License Report ##
                              |${licenses.mkString("\t", "\n\t", "\n")}
                              |""".stripMargin
}

object LicenseReport {

  val mvnSearchUrl = "http://search.maven.org/remotecontent?filepath="

  val tremorHeaders = Seq("Open Source Name", "Version", "License", "Link to License", "Dual-Licensed?", "Link to Source",
    "Products", "Function Type", "Interaction", "Modified", "Distribution (Downloadable, Internally Used, SaaS)", "Compiled Together")

  val defaultHeaders = Seq("Category", "License", "Dependency", "Notes")

  def convertDotToSlash(groupId: String): String = {
    groupId.replaceAll("\\.", "/")
  }

  def generateMavenUrl(groupId: String, artifactId: String, version: String) = {
    val gId = convertDotToSlash(groupId)
    val aId = convertDotToSlash(artifactId)
    mvnSearchUrl + gId + "/" + aId + "/" + version.trim + "/" + s"${artifactId}-${version.trim}.pom"
  }

  def get(url: String): String = {

    val response = Http(url).option(_.setInstanceFollowRedirects(false)).asString
    val resp = get(url, response)

    if (resp == null) {
      ""
    } else {
      resp.body
    }
  }

  def get(url: String, resp: HttpResponse[String]): HttpResponse[String] = {
    if (url.isEmpty)
      return resp

    val response = Http(url).option(_.setInstanceFollowRedirects(false)).asString

    if (response.code == 404)
      return null

    if (response.header("Location").nonEmpty) {
      val redirect = response.header("Location").get
      get(response.header("Location").get, response)
    } else {
      get("", response)
    }
  }

  def findLicenseThroughParent(xml: Elem): (String, String) = {
    var licensesNode = xml \ "licenses"
    var parent = xml \ "parent"
    while (licensesNode.isEmpty && parent.nonEmpty) {
      val groupId = parent \ "groupId" text
      val artifactId = parent \ "artifactId" text
      val version = parent \ "version" text

      val mvnUrl = generateMavenUrl(groupId, artifactId, version)

      val r = get(mvnUrl)
      val pom = scala.xml.XML.loadString(r)

      licensesNode = pom \ "licenses"
      parent = pom \ "parent"
    }

    val name = licensesNode \ "license" \ "name" text
    val url = licensesNode \ "license" \ "url" text

    (name, url)
  }

  def getLicenseFromMaven(groupId: String, artifactId: String, version: String): (String, String) = {

    val mvnUrl = generateMavenUrl(groupId, artifactId, version)
    val r = get(mvnUrl)

    if (r.nonEmpty) {
      val pom = scala.xml.XML.loadString(r)
      findLicenseThroughParent(pom)
    } else {
      ("", "")
    }
  }

  def withPrintableFile(file: File)(f: (Any => Unit) => Unit): Unit = {
    IO.createDirectory(file.getParentFile)
    Using.fileWriter(java.nio.charset.Charset.defaultCharset, false)(file) { writer =>
      def println(msg: Any): Unit = {
        writer.write(msg.toString)
        //writer.newLine()
      }
      f(println _)
    }
  }

  def dumpTremorLicenseReport(report: LicenseReport, config: LicenseReportConfiguration, projectName: String): Unit = {
    import config._
    val ordered = report.licenses.filter(l => licenseFilter(l.license.category)) sortWith {
      case (l, r) =>
        if (l.license.category != r.license.category) l.license.category.name < r.license.category.name
        else {
          if (l.license.name != r.license.name) l.license.name < r.license.name
          else {
            l.module.toString < r.module.toString
          }
        }
    }
    // TODO - Make one of these for every configuration?
    for (language <- languages) {
      val reportFile = new File(config.reportDir, s"${title}.${language.ext}")
      withPrintableFile(reportFile) { print =>
        print(language.documentStart(title, reportStyleRules))
        print(makeHeader(language))
        print(language.tableHeader(tremorHeaders))
        for (dep <- ordered) {

          print(language.tableRow(
            Seq(
              s"${dep.module.organization}:${dep.module.name}",
              dep.module.version,
              dep.license.name,
              if (dep.license.url == null) "" else dep.license.url,
              "No",
              if (dep.module.url == null) "" else dep.module.url,
              projectName, // Product,
              "Java Library",
              "Used via API",
              "No",
              if (dep.configs.contains("test") && dep.configs.size == 1) "Internally Used" else "SaaS",
              "No"
            )))
        }
        print(language.tableEnd)
        print(language.documentEnd)
      }
    }
  }

  def dumpLicenseReport(report: LicenseReport, config: LicenseReportConfiguration): Unit = {
    import config._
    val ordered = report.licenses.filter(l => licenseFilter(l.license.category)) sortWith {
      case (l, r) =>
        if (l.license.category != r.license.category) l.license.category.name < r.license.category.name
        else {
          if (l.license.name != r.license.name) l.license.name < r.license.name
          else {
            l.module.toString < r.module.toString
          }
        }
    }
    // TODO - Make one of these for every configuration?
    for (language <- languages) {
      val reportFile = new File(config.reportDir, s"${title}.${language.ext}")
      withPrintableFile(reportFile) { print =>
        print(language.documentStart(title, reportStyleRules))
        print(makeHeader(language))
        print(language.tableHeader(defaultHeaders))
        for (dep <- ordered) {
          val licenseLink = language.createHyperLink(dep.license.url, dep.license.name)
          print(language.tableRow(
            Seq(dep.license.category.name,
              licenseLink,
              dep.module.toString,
              notes(dep.module) getOrElse "")))
        }
        print(language.tableEnd)
        print(language.documentEnd)
      }
    }
  }
  def getModuleInfo(dep: IvyNode): DepModuleInfo = {
    // TODO - null handling...
    DepModuleInfo(dep.getModuleId.getOrganisation, dep.getModuleId.getName, dep.getModuleRevision.getId.getRevision,
      dep.getDescriptor.getHomePage)
  }

  def makeReport(module: IvySbt#Module, configs: Set[String], licenseSelection: Seq[LicenseCategory], overrides: DepModuleInfo => Option[LicenseInfo], log: Logger): LicenseReport = {
    val (report, err) = resolve(module, log)
    err foreach (x => throw x) // Bail on error
    makeReportImpl(report, configs, licenseSelection, overrides, log)
  }
  /**
   * given a set of categories and an array of ivy-resolved licenses, pick the first one from our list, or
   *  default to 'none specified'.
   */
  def pickLicense(categories: Seq[LicenseCategory], groupId: String, artifactId: String, version: String)(licenses: Array[org.apache.ivy.core.module.descriptor.License]): LicenseInfo = {
    if (licenses.isEmpty) {
      return LicenseInfo(LicenseCategory.NoneSpecified, "", "")
    }

    // We look for a license matching the category in the order they are defined.
    // i.e. the user selects the licenses they prefer to use, in order, if an artifact is dual-licensed (or more)
    for (category <- categories) {
      for (license <- licenses) {
        if (category.unapply(license.getName)) {
          return LicenseInfo(category, license.getName, license.getUrl)
        }
      }
    }
    val license = licenses(0)

    if (license.getName == "none specified" && license.getUrl == "none specified") {
      val mvnLicense = getLicenseFromMaven(groupId, artifactId, version)
      return LicenseInfo(LicenseCategory(mvnLicense._1), mvnLicense._1, mvnLicense._2)
    }

    LicenseInfo(LicenseCategory.Unrecognized, license.getName, license.getUrl)
  }
  /** Picks a single license (or none) for this dependency. */
  def pickLicenseForDep(dep: IvyNode, configs: Set[String], categories: Seq[LicenseCategory]): Option[DepLicense] =
    for {
      d <- Option(dep)
      cs = dep.getRootModuleConfigurations.toSet
      filteredConfigs = if (cs.isEmpty) cs else cs.filter(configs)
      if !filteredConfigs.isEmpty
      if !filteredConfigs.forall(d.isEvicted)
      desc <- Option(dep.getDescriptor)
      licenses = Option(desc.getLicenses).filterNot(_.isEmpty).getOrElse(Array(new org.apache.ivy.core.module.descriptor.License("none specified", "none specified")))
      // TODO - grab configurations.
    } yield {
      val depModuleInfo = getModuleInfo(dep)
      DepLicense(depModuleInfo, pickLicense(categories, depModuleInfo.organization, depModuleInfo.name, depModuleInfo.version)(licenses), filteredConfigs)
    }

  def getLicenses(report: ResolveReport, configs: Set[String] = Set.empty, categories: Seq[LicenseCategory] = LicenseCategory.all): Seq[DepLicense] = {
    import collection.JavaConverters._
    for {
      dep <- report.getDependencies.asInstanceOf[java.util.List[IvyNode]].asScala
      report <- pickLicenseForDep(dep, configs, categories)
    } yield report
  }

  def makeReportImpl(report: ResolveReport, configs: Set[String], categories: Seq[LicenseCategory], overrides: DepModuleInfo => Option[LicenseInfo], log: Logger): LicenseReport = {
    import collection.JavaConverters._
    val licenses = getLicenses(report, configs, categories) map { l =>
      overrides(l.module) match {
        case Some(o) => l.copy(license = o)
        case _ => l
      }
    }
    // TODO - Filter for a real report...
    LicenseReport(licenses, report)
  }

  // Hacky way to go re-lookup the report
  def resolve(module: IvySbt#Module, log: Logger): (ResolveReport, Option[ResolveException]) =
    module.withModule(log) { (ivy, desc, default) =>
      import org.apache.ivy.core.resolve.ResolveOptions
      val resolveOptions = new ResolveOptions
      val resolveId = ResolveOptions.getDefaultResolveId(desc)
      resolveOptions.setResolveId(resolveId)
      import org.apache.ivy.core.LogOptions.LOG_QUIET
      resolveOptions.setLog(LOG_QUIET)
      val resolveReport = ivy.resolve(desc, resolveOptions)
      val err =
        if (resolveReport.hasError) {
          val messages = resolveReport.getAllProblemMessages.toArray.map(_.toString).distinct
          val failed = resolveReport.getUnresolvedDependencies.map(node => IvyRetrieve.toModuleID(node.getId))
          Some(new ResolveException(messages, failed))
        } else None
      (resolveReport, err)
    }

}
