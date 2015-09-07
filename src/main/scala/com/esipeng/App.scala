package com.esipeng

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.esipeng.diameter.AsyncDiameterActor
import com.esipeng.restful.AsyncHttpRestActor
import org.slf4j.LoggerFactory
import spray.can.Http

/**
 * @author ${user.name}
 */
object App {

  def main(args : Array[String]) {
    val log = LoggerFactory.getLogger("MainApp")
    implicit val system = ActorSystem("Server")
    val localAddress = system.settings.config.getString("http_interface.local-address")
    val localPort = system.settings.config.getString("http_interface.local-port")




    log.info(s"Http Rest interface: $localAddress:$localPort")
    val diameterActor = system.actorOf(Props[AsyncDiameterActor])
    val httpInterface = system.actorOf(Props(classOf[AsyncHttpRestActor],diameterActor))
    IO(Http) ! Http.Bind(listener = httpInterface,interface = localAddress,port = localPort.toInt)


  }

}
