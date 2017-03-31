package org.multibot

object Multibottest extends App {
  val roomsToJoin: List[String] = "MetaElysium/MetabotTestRoom" :: "OlegYch/multibot" :: Nil
  val cache = InterpretersCache(roomsToJoin)
  val gitterPass =
    Option(System getenv "MULTIBOT_GITTER_PASS")
    .getOrElse("this isn't a password")
  GitterBot(cache, gitterPass, roomsToJoin).start
}
