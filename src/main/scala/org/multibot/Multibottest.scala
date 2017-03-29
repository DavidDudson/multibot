package org.multibot

object Multibottest extends App {
  val cache = InterpretersCache(List("MetaElysium/MetabotTestRoom"))
  val gitterPass = Option(System getenv "MULTIBOT_GITTER_PASS").getOrElse("this isn't a password")
  GitterBot(cache).start
}
