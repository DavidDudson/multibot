package org.multibot.irc

import InterpretersCache

object Multibottest {
  def main(args: Array[String]): Unit = {
    val cache = InterpretersCache(List("#Elysium", "scalameta/scalameta"))
    val gitterPass = Option(System getenv "MULTIBOT_GITTER_PASS").getOrElse("this isn't a password")
    IrcMultibot(cache = cache,
      botname = "metabot",
      channels = List("MetaElysium/MetabotTestRoom", "scalameta/scalameta"),
      settings = _.setServerHostname("irc.gitter.im").setServerPassword(gitterPass).
        setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault)
    ).start()
    while (scala.io.StdIn.readLine() != "exit") Thread.sleep(1000)
    sys.exit()
  }
}
