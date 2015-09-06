package com.esipeng.diameter

import com.esipeng.diameter.node._
import org.slf4j.LoggerFactory
import DiameterConstants._
/**
 * Created by esipeng on 9/2/2015.
 */
case class SyncObj(var answer:Message,var ready:Boolean)

class DiameterServer(settings:NodeSettings,peer:Peer) extends NodeManager(settings){
  val log = LoggerFactory.getLogger("DiameterServer")
  override def start = {
    super.start
    node().initiateConnection(peer,true)
    waitForConnection(500)
  }

  override def handleAnswer(message: Message, connectionKey: ConnectionKey, syncObj: scala.Any): Unit = {
    val sync = syncObj.asInstanceOf[SyncObj]
    sync.synchronized  {
      sync.answer = message
      sync.ready = true
      sync.notify()
    }
  }

  def sendRequest(message:Message,wait:Long):Message =  {
    val sync = SyncObj(null,false)
    val waitUntil = System.currentTimeMillis() + wait

    try {
      super.sendRequest(message,Array[Peer](peer),sync,wait)

      sync.synchronized {
        if(wait >= 0) { //there is timeout set
          val current = System.currentTimeMillis()
          val gap = waitUntil - current
          if(gap > 0) { //still has some time to wait
            while(System.currentTimeMillis() < waitUntil && sync.ready == false)
              sync.wait(gap)
          }
        } else  { //wait forever!
          while(sync.ready == false)
            sync.wait
        }
      }
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

    sync.answer
  }

  def sendRequest(message:Message):Message  = {
    sendRequest(message,-1L)
  }


}


object DiameterServer {
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
    new DiameterServer(settings,hssPeer)
  }
}