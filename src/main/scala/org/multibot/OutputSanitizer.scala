package org.multibot

object OutputSanitizer {

  val STRING_OUTPUT = "String = "

  def apply(s: String): String = {
    val adjusted = if (s.startsWith(STRING_OUTPUT)) {
      val stripped = s.stripPrefix(STRING_OUTPUT)
      raw"""$STRING_OUTPUT"$stripped""""
    } else {
      s
    }

    adjusted
      .replace("\r", "")
      .replace("`", "\'")
  }
}
