package org.multibot

import java.util
import java.util.concurrent.TimeUnit

import com.amatkivskiy.gitter.sdk.async.faye.client.{AsyncGitterFayeClient, AsyncGitterFayeClientBuilder}
import com.amatkivskiy.gitter.sdk.async.faye.listeners.RoomMessagesChannel
import com.amatkivskiy.gitter.sdk.async.faye.model.MessageEvent
import com.amatkivskiy.gitter.sdk.model.response.message.MessageResponse
import com.amatkivskiy.gitter.sdk.model.response.room.RoomResponse
import com.google.common.cache.{Cache, CacheBuilder}
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

import scala.util.Try

case class GitterBot(cache: InterpretersCache, accountToken: String, roomsToJoin: List[String]) {

  private final val LOGGER = LoggerFactory.getLogger(GitterBot.getClass)

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

  val rest: SafeSyncGitterApiClient = new SafeSyncGitterApiClient.Builder()
    .withAccountToken(accountToken)
    .build()

  val faye: AsyncGitterFayeClient = new AsyncGitterFayeClientBuilder()
    .withAccountToken(accountToken)
    .withFailListener(e => LOGGER.warn("Faye issue", e))
    .withOnDisconnected(() => start())
    .withOkHttpClient(new OkHttpClient())
    .build()

  def connectToChannel(id: String): Unit = {

    faye.subscribe(new RoomMessagesChannel(id) {

      override def onSubscribed(channel: String, messagesSnapshot: util.List[MessageResponse]): Unit =
        LOGGER.info(s"subscribed")

      override def onFailed(channel: String, ex: Exception): Unit =
        LOGGER.warn(s"subscribeFailed to $ex")

      def isMessageIdInCache(id: String): Boolean =
        recentMessageIdCache.getIfPresent(id) != null

      @Override
      def onMessage(channel: String, message: MessageEvent) {
        val messageId = message.message.id
        message.message.text match {
          case "ping" =>
            rest.sendMessage(id, "pong")
          case PlainInterpretableMessage(input) =>
            updateIncomingMessage(messageId, input)
            create(messageId, input)
          case IntepretableMessage(input) if isCreate(message) =>
            updateIncomingMessage(messageId, input)
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
        * If someone were to write
        * ! 1 + 1
        *
        * It would be wrapped like
        *
        * ```scala
        * ! 1 + 1
        * ```
        *
        * Note this only works when the bot OWNS the room.
        * Admin privileges is not enough.
        *
        * It actually suprises me that this works at all, since it is not possible via the web GUI
        */
      private def updateIncomingMessage(messageId: String, input: String) = {
        rest.getCurrentUserRooms
          .filter(_.contains((r: RoomResponse) => r.id == id))
          .foreach { _ =>
            LOGGER.info("Wrapping plain message")
            rest.updateMessage(id, messageId, Sanitizer.sanitizeOutput(input))
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
        rest.getRoomMessages(id)
          .filter(_.last.id == messageId)
          .isSuccess
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
      response.foreach {
        r => recentMessageIdCache.put(messageId, r.id)
      }
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
      reponse.foreach { r =>
        recentMessageIdCache.put(messageId, r.id)
      }
    }
  }

  def start(): Unit = {
    Try {
      faye.connect(() => {
        rest.getCurrentUser.map { u =>
          LOGGER.info(s"connected as ${u.displayName}")
        } getOrElse restRecovery

        roomsToJoin
          .map(rest.searchRooms(_, 1).getOrElse(Nil))
          .map(_.head)
          .map(_.id)
          .foreach(connectToChannel)
      })
    } recover fayeRecovery
  }

  def fayeRecovery: PartialFunction[Throwable, Unit] = {
      case e =>
        LOGGER.warn("restarting faye", e)
        start()
  }

  def restRecovery: PartialFunction[Throwable, Unit] = {
    case e =>
      faye.disconnect()
      LOGGER.warn("rest error", e)
      start()
  }
}
