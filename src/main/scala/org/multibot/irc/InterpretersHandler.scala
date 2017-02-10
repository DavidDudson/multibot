package org.multibot.irc

case class InterpretersHandler(cache: InterpretersCache, sendLines: (String, String) => Unit) {
  def serve(implicit msg: Msg): Unit = msg.message match {
    case Cmd("!" :: m :: Nil) => sendLines(msg.channel, cache.scalaInterpreter(msg.channel) { (si, cout) =>
      import scala.tools.nsc.interpreter.Results._
      si interpret m match {
        case Success => cout.toString.replaceAll("(?m:^res[0-9]+: )", "")
        case Error => cout.toString.replaceAll("^<console>:[0-9]+: ", "")
        case Incomplete => "error: unexpected EOF found, incomplete expression"
      }
    })

    case Cmd("!type" :: m :: Nil) => sendLines(msg.channel, cache.scalaInterpreter(msg.channel)((si, cout) => si.typeOfExpression(m).directObjectString))
    case "!reset" => cache.scalaInt invalidate msg.channel
    case "!reset-all" => cache.scalaInt.invalidateAll()
    case _ =>
  }
}
