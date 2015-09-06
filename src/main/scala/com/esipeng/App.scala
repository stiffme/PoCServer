package com.esipeng

import akka.actor.{Props, ActorSystem}
import akka.io.IO
import com.esipeng.diameter.DiameterActor
import com.esipeng.restful.HttpRestActor
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
    val diameterActor = system.actorOf(Props[DiameterActor])
    val httpInterface = system.actorOf(Props(classOf[HttpRestActor],diameterActor))
    IO(Http) ! Http.Bind(listener = httpInterface,interface = localAddress,port = localPort.toInt)


  }

}
