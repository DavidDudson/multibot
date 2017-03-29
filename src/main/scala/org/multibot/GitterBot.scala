package org.multibot

import java.util
import java.util.concurrent.TimeUnit

import com.amatkivskiy.gitter.sdk.async.faye.client.{AsyncGitterFayeClient, AsyncGitterFayeClientBuilder}
import com.amatkivskiy.gitter.sdk.async.faye.listeners.RoomMessagesChannel
import com.amatkivskiy.gitter.sdk.async.faye.model.MessageEvent
import com.amatkivskiy.gitter.sdk.model.response.message.MessageResponse
import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient
import com.google.common.cache.{Cache, CacheBuilder}
import okhttp3.OkHttpClient

import scala.collection.JavaConverters._

case class GitterBot(cache: InterpretersCache) {

  private val accountToken = "5983838e1a4c54582fecdeb1bcbf69cb59888381"

  /**
    * Input => Output id cache
    * We use this to keep a mapping of which messages we have replied to.
    * This is so if they are updated we can update our response
    */
  private val recentMessageIdCache: Cache[String, String] =
    CacheBuilder.newBuilder()
      .maximumSize(100)
      .expireAfterWrite(10, TimeUnit.MINUTES) // Same as gitter edit time
      .build[String, String]()

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
        println(s"subscribed to $channel")

      override def onFailed(channel: String, ex: Exception): Unit =
        println(s"subscribeFailed to $ex")

      def isMessageIdInCache(id: String): Boolean =
        recentMessageIdCache.getIfPresent(id) != null

      @Override
      def onMessage(channel: String, message: MessageEvent) {
        val text = message.message.text
        println(message.operation)
        println(s"message $text")

        val messageId = message.message.id
        text match {
          case "ping" =>
            rest.sendMessage(id, "pong")
          case IntepretableMessage(input) if isMessageIdInCache(messageId) && message.operation == "update" =>
            val output = interpret(input)
            val oldId = recentMessageIdCache.getIfPresent(messageId)
            val reponse = rest.updateMessage(id, oldId, Sanitizer.sanitizeOutput(output))
            recentMessageIdCache.put(messageId, reponse.id)
          case IntepretableMessage(input) if message.operation == "create" =>
            val output = interpret(input)
            val reponse = rest.sendMessage(id, Sanitizer.sanitizeOutput(output))
            recentMessageIdCache.put(messageId, reponse.id)
            println(s"sending $output")
          case _ if isMessageIdInCache(messageId) && message.operation == "update" =>
            val oldId = recentMessageIdCache.getIfPresent(messageId)
            Option(oldId)
              .foreach {
                rest.updateMessage(id, oldId, "")
                recentMessageIdCache.invalidate
              }
            println("removing message because it was edited to a non-command")
          case _ if message.operation == "remove" =>
            val oldId = recentMessageIdCache.getIfPresent(messageId)
            Option(oldId)
              .foreach {
                rest.updateMessage(id, oldId, "")
                recentMessageIdCache.invalidate
              }
            println("removing message")
          case _ =>
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
