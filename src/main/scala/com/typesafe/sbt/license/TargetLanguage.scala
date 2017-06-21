package com.typesafe.sbt
package license

/**
 * Hooks for generating "rich" text documents.  Borrowed from scala/make-release-notes
 */
sealed trait TargetLanguage {
  def documentStart(title: String, reportStyleRules: Option[String]): String
  def documentEnd(): String
  /** Create a link with the given content string as the displayed text. */
  def createHyperLink(link: String, content: String): String
  /** Add a blank line. Note: For HTML may not be a blank string. */
  def blankLine(): String
  /** Creates something equivalent to an html &lt;h1&gt; tag. */
  def header1(msg: String): String
  /** The syntax for the header of a table. */
  def tableHeader(columns: Seq[String]): String
  /** The syntax for a row of a table. */
  def tableRow(columns: Seq[String]): String
  /** And a "table" */
  def tableEnd: String
  /** File extension for this style of report. */
  def ext: String
}
case object MarkDown extends TargetLanguage {
  val ext = "md"
  // TODO - Header for markdown?
  def documentStart(title: String, reportStyleRules: Option[String]): String = ""
  def documentEnd(): String = ""
  def createHyperLink(link: String, content: String): String =
    s"[$content]($link)"
  def blankLine(): String = "\n"
  def header1(msg: String): String = s"# $msg\n"
  def tableHeader(columns: Seq[String]): String = {
    columns.mkString(" | ") + "\n" + columns.map(_ => "--- | ").dropRight(2)
  }

  def tableRow(columns: Seq[String]): String = {
    columns.map { s =>
      if (s.contains("|"))
        s"<notextile>${escapeHtml(s)}</notextile> | "
      else
        s"$s | "
    }.dropRight(2) + "\n"
  }

  def tableEnd: String = "\n"

  def markdownEncode(s: String): String = s.flatMap {
    case c if (List('*', '`', '[', ']', '#').contains(c)) => "\\" + c
    case x => x.toString
  }

  def escapeHtml(s: String): String = Html.htmlEncode(s).flatMap {
    case '|' => "&#124;" // it would destroy tables!
    case c => c.toString
  }
}
case object Html extends TargetLanguage {
  val ext = "html"

  def documentStart(title: String, reportStyleRules: Option[String]): String = {
    val style = reportStyleRules map (rules => s"""<style media="screen" type="text/css">$rules</style>""")

    s"""<html><head><title>${title}</title>${style.getOrElse("")}</head><body>"""
  }

  def documentEnd(): String = "</body></html>"
  def createHyperLink(link: String, content: String): String =
    s"""<a href="$link">$content</a>"""
  def blankLine(): String = "<p>&nbsp;</p>"
  def header1(msg: String): String = s"<h1>$msg</h1>"
  def tableHeader(columns: Seq[String]): String =
    s"""<table border="0" cellspacing="0" cellpading="1">
      <thead><tr>${columns.map(c => s"<th>$c</th>")}</tr></thead>
    <tbody>"""
  def tableRow(columns: Seq[String]): String = {
    s"""<tr>${columns.map(c => s"<td>${htmlEncode(c)}</td>")}</tr>"""
  }

  def tableEnd: String = "</tbody></table>"

  def htmlEncode(s: String) = org.apache.commons.lang3.StringEscapeUtils.escapeHtml4(s)
}

case object Csv extends TargetLanguage {
  val ext = "csv"
  def documentStart(title: String, reportStyleRules: Option[String]): String = ""
  def documentEnd(): String = ""
  def createHyperLink(link: String, content: String): String = {
    if (link != null && !link.trim().isEmpty()) s"$content ($link)" else s"$content"
  }
  def blankLine(): String = ""
  def header1(msg: String): String = ""
  def tableHeader(columns: Seq[String]): String = tableRow(columns)
  def tableRow(columns: Seq[String]): String = columns.mkString(",") + "\n"

  def tableEnd: String = ""
  def csvEncode(s: String): String = org.apache.commons.lang3.StringEscapeUtils.escapeCsv(s)
}
