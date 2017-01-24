package org.multibot

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Future
object GitterHttpClient {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val wsClient = AhcWSClient()

  wsClient.url("http://www.google.com").get().map { response =>
    val statusText: String = response.statusText
    println(s"Got a response $statusText")
  }

}
