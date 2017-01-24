package org.multibot

object OutputSanitizer {

  def apply(s: String): String =
      s.replace("\r", "")
       .replace("`", "\'")
}
