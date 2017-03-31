package org.multibot

import java.util
import java.util.concurrent.TimeUnit

import com.amatkivskiy.gitter.sdk.async.faye.client.{AsyncGitterFayeClient, AsyncGitterFayeClientBuilder}
import com.amatkivskiy.gitter.sdk.async.faye.listeners.RoomMessagesChannel
import com.amatkivskiy.gitter.sdk.async.faye.model.MessageEvent
import com.amatkivskiy.gitter.sdk.model.response.message.MessageResponse
import com.amatkivskiy.gitter.sdk.model.response.room.RoomResponse
import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient
import com.google.common.cache.{Cache, CacheBuilder}
import okhttp3.OkHttpClient

import scala.collection.JavaConverters._

case class GitterBot(cache: InterpretersCache, accountToken: String, roomsToJoin: List[String]) {


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

  val faye: AsyncGitterFayeClient = new AsyncGitterFayeClientBuilder()
    .withAccountToken(accountToken)
    .withFailListener(println)
    .withOnDisconnected(() => println("disconnect"))
    .withOkHttpClient(new OkHttpClient())
    .build()

  def connectToChannel(id: String): Unit = {

    faye.subscribe(new RoomMessagesChannel(id) {

      override def onSubscribed(channel: String, messagesSnapshot: util.List[MessageResponse]): Unit =
        println(s"subscribed")

      override def onFailed(channel: String, ex: Exception): Unit =
        println(s"subscribeFailed to $ex")

      def isMessageIdInCache(id: String): Boolean =
        recentMessageIdCache.getIfPresent(id) != null

      @Override
      def onMessage(channel: String, message: MessageEvent) {
        val messageId = message.message.id
        message.message.text match {
          case "ping" =>
            rest.sendMessage(id, "pong")
          case PlainInterpretableMessage(input) =>
            if (rest.getCurrentUserRooms.contains((r: RoomResponse) => r.id == id)) {
              println("Wrapping plain message")
              rest.updateMessage(id, messageId, Sanitizer.sanitizeOutput(input))
            }
            create(messageId, input)

          case IntepretableMessage(input) if isCreate(message) =>
            create(messageId, input)
          case IntepretableMessage(input) if isUpdateOfCommand(message, messageId) =>
            update(messageId, input)
          case IntepretableMessage(input) if isUpdateOfNonCommand(message, messageId) && isLastMessage(messageId) =>
            update(messageId, input)
          case _ if isUpdateOfCommand(message, messageId) =>
            delete(messageId)
          case _ if isRemove(message) =>
            delete(messageId)
          case _ =>
        }
      }

      /**
        * Means that the message was updated, and we have already interpreted it before
        */
      def isUpdateOfNonCommand(message: MessageEvent, messageId: String): Boolean =
        !isMessageIdInCache(messageId) && message.operation == "update"


      /**
        * Means that the message was updated, and we have not interpreted it before
        */
      def isUpdateOfCommand(message: MessageEvent, messageId: String): Boolean =
        isMessageIdInCache(messageId) && message.operation == "update"

      def isCreate(message: MessageEvent): Boolean =
        message.operation == "create"

      private def isRemove(message: MessageEvent) =
        message.operation == "remove"

      /**
        * Means that the message is the last message in the channel
        */
      def isLastMessage(messageId: String): Boolean =
        rest.getRoomMessages(id).asScala.last.id == messageId
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

    def create(messageId: String, input: String) = {
      val response = rest.sendMessage(id, Sanitizer.sanitizeOutput(interpret(input)))
      recentMessageIdCache.put(messageId, response.id)
    }

    def delete(messageId: String) = {
      val oldId = recentMessageIdCache.getIfPresent(messageId)
      Option(oldId)
        .foreach {
          rest.updateMessage(id, oldId, "")
          recentMessageIdCache.invalidate
        }
    }

    def update(messageId: String, input: String) = {
      val oldId = recentMessageIdCache.getIfPresent(messageId)
      val reponse = rest.updateMessage(id, oldId, Sanitizer.sanitizeOutput(interpret(input)))
      recentMessageIdCache.put(messageId, reponse.id)
    }
  }

  def start(): Unit = {
    faye.connect(() => {
      println(s"connected as ${rest.getCurrentUser.displayName}")

      roomsToJoin
        .map(rest.searchRooms(_, 1))
        .map(_.asScala.head)
        .map(_.id)
        .foreach(connectToChannel)
    })
  }
}
