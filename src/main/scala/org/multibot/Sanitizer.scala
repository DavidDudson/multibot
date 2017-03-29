package org.multibot

object Sanitizer {

  def sanitizeOutput(out: String): String =
    "```scala\n" + out.replace("`", "\'").trim + "\n```"

  def sanitizeInput(in: String): String = {
    val tripleBackquoted = "^```(.*)```$".r
    val singleBackquoted = "^`(.*)`$".r

    in match {
      case tripleBackquoted(s) => s
      case singleBackquoted(s) => s
      case s => s
    }
  }
}
