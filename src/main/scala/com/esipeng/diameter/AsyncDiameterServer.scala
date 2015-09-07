package com.esipeng.diameter

import com.esipeng.diameter.DiameterConstants._
import com.esipeng.diameter.node._
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
/**
 * Created by esipeng on 9/2/2015.
 */

class AsyncDiameterServer(settings:NodeSettings,peer:Peer) extends NodeManager(settings){
  val log = LoggerFactory.getLogger("DiameterServer")
  override def start = {
    super.start
    node().initiateConnection(peer,true)
    waitForConnection(500)
  }

  override def handleAnswer(message: Message, connectionKey: ConnectionKey, syncObj: scala.Any): Unit = {
    super.handleAnswer(message,connectionKey,syncObj)
    val sync = syncObj.asInstanceOf[Promise[Message]]
    sync.success(message)
  }

  def sendRequest(message:Message,wait:Long):Future[Message] =  {
    val sync = Promise[Message]

    try {
      super.sendRequest(message,Array[Peer](peer),sync,wait)

    } catch {
      case notRoutable:NotRoutableException => {
        log.warn("Not routable diameter message, re-init connection")
        node().initiateConnection(peer,true)
        waitForConnection(500)
      }
      case e:InterruptedException => {}
      case notRequest:NotARequestException => {
        log.error("message is not a request")
      }
    }

    sync.future
  }

  def sendRequest(message:Message):Future[Message]  = {
    sendRequest(message,-1L)
  }


}


object AsyncDiameterServer {
  def createServer(destinationHost:String,destinationPort:Int,destinationIp:String,originHost:String,originRealm:String) = {
    val capabilities = new Capability()
    //add Sh interface capability
    capabilities.addVendorAuthApp(TGPP,ShAppId)

    //construct nodeSettings
    val settings = new NodeSettings(originHost,originRealm,TGPP,capabilities,0,"PocClient",0)
    settings.setUseSCTP(false)
    settings.setUseTCP(true)
    //construct hss peer
    val hssPeer = new Peer(destinationHost,destinationPort,Peer.TransportProtocol.tcp)
    hssPeer.setRealAddress(destinationIp)
    //return new DiameterServer
    new AsyncDiameterServer(settings,hssPeer)
  }
}