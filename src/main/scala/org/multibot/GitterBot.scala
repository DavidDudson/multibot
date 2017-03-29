package org.multibot

import java.util

import com.amatkivskiy.gitter.sdk.async.faye.client.{AsyncGitterFayeClient, AsyncGitterFayeClientBuilder}
import com.amatkivskiy.gitter.sdk.async.faye.listeners.RoomMessagesChannel
import com.amatkivskiy.gitter.sdk.async.faye.model.MessageEvent
import com.amatkivskiy.gitter.sdk.model.response.message.MessageResponse
import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient
import okhttp3.OkHttpClient

import scala.collection.JavaConverters._

case class GitterBot(cache: InterpretersCache) {

  private val accountToken = "5983838e1a4c54582fecdeb1bcbf69cb59888381"

  val rest: SyncGitterApiClient = new SyncGitterApiClient.Builder()
    .withAccountToken(accountToken)
    .build()

  val roomsToJoin: List[String] = "MetaElysium/MetabotTestRoom" :: "OlegYch/multibot" :: Nil

  val faye: AsyncGitterFayeClient = new AsyncGitterFayeClientBuilder()
    .withAccountToken(accountToken)
    .withFailListener(println)
    .withOnDisconnected(() => println("disconnect"))
    .withOkHttpClient(new OkHttpClient())
    .build()

  def connectToChannel(id: String): Unit = {
    faye.subscribe(new RoomMessagesChannel(id) {

      override def onSubscribed(channel: String, messagesSnapshot: util.List[MessageResponse]): Unit =
        println("subscribed")

      override def onFailed(channel: String, ex: Exception): Unit =
        println(s"subscribeFailed to $ex")

      @Override
      def onMessage(channel: String, message: MessageEvent) {
        val text = message.message.text
        if (text == null) {
          return
        }
        println(s"message $text")
        if (text == "ping")
          rest.sendMessage(id, "pong")

        if (text.trim.startsWith("! ")) {
          val output = Sanitizer.sanitizeOutput(Sanitizer.sanitizeInput(interpret(text.drop(2))))
          rest.sendMessage(id, output)
          println(s"sending $output")
        } else {
          println(s"ignored $text")
        }
      }
    })

    def interpret(s: String): String = {
      cache.scalaInterpreter(id) { (si, cout) =>
        var out = ""
        import scala.tools.nsc.interpreter.Results._

        si interpret Sanitizer.sanitizeInput(s) match {
          case Success =>
            out += cout.toString.replaceAll("(?m:^res[0-9]+: )", "")
            out += "\n"
          case Error =>
            out += cout.toString.replaceAll("^<console>:[0-9]+: ", "")
            out += "\n"
          case Incomplete =>
            out += "error: unexpected EOF found, incomplete expression"
            out += "\n"
        }

        out
      }
    }
  }

  def start(): Unit = {
    faye.connect(() => {
      println("connected")

      roomsToJoin
        .map(rest.searchRooms(_, 1))
        .map(_.asScala.head)
        .map(_.id)
        .foreach(connectToChannel)
    })
  }
}
