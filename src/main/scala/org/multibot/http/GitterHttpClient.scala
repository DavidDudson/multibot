package org.multibot.http
object GitterHttpClient {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val wsClient = AhcWSClient()

  wsClient.url("http://www.google.com").get().map { response =>
    val statusText: String = response.statusText
    println(s"Got a response $statusText")
  }

}
