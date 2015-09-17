package com.esipeng.diameter

import java.io.{StringReader, StringWriter}

import akka.actor.{Actor, ActorLogging, Cancellable}
import com.esipeng.diameter.DiameterConstants._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.xml.XML
/**
 * Created by esipeng on 9/2/2015.
 */
case class SigAsyncRequestData(impu:String)
case class SigAsyncRequestDataResult(repoData:Future[Option[RepoData]])
//case class SigAsyncUpdateData(impu:String,data:Map[String,Int])
//case class SigAsyncUpdateDataResult(success:Future[Boolean])
case class SigAsyncDeleteData(impu:String)
case class SigAsyncDeleteKeyData(impu:String,keyword:String)
case class SigAsyncDeleteDataResult(success:Future[Boolean])
case class SigAsyncAddData(impu:String,data:Seq[String])
case class SigAsyncAddDataResult(success:Future[Boolean])
case class RepoData(seq:Int,data:Map[String,Int])

protected case object CheckConnection

class AsyncDiameterActor extends Actor with ActorLogging{
  val destinationAddress = context.system.settings.config.getString("diameter.destination-address")
  val destinationPort = context.system.settings.config.getString("diameter.destination-port").toInt
  val destinationHost = context.system.settings.config.getString("diameter.destination-host")
  val destinationRealm = context.system.settings.config.getString("diameter.destination-realm")

  val originHost = context.system.settings.config.getString("diameter.origin-host")
  val originRealm = context.system.settings.config.getString("diameter.origin-realm")

  val serviceIndication =  context.system.settings.config.getString("diameter.service-indication")
  log.info(s"Diameter configuration Address $destinationAddress:$destinationPort, $destinationHost $destinationRealm, origin $originHost $originRealm")
  var diameterServer:AsyncDiameterServer = null
  implicit val executor = context.system.dispatcher

  var sheduleCheck:Cancellable = _

  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    //start diameter server
    diameterServer = AsyncDiameterServer.createServer(destinationHost,destinationPort,destinationAddress,originHost,originRealm)
    diameterServer.start

    //schedule every 3 seconds to check the connection
    sheduleCheck = context.system.scheduler.schedule(500 millis,3 second,self,CheckConnection)
  }


  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    super.postStop
    diameterServer.stop(2000)
    if(sheduleCheck != null) sheduleCheck.cancel()
  }

  def receive = {
    case SigAsyncRequestData(impu) => sender ! SigAsyncRequestDataResult(requestData(impu))
    //case SigAsyncUpdateData(impu,data) => sender ! SigAsyncUpdateDataResult(updateData(impu,data))
    case SigAsyncDeleteData(impu) => sender ! SigAsyncDeleteDataResult(deleteUserData(impu))
    case SigAsyncAddData(impu,data) => sender ! SigAsyncAddDataResult(addData(impu,data))
    case SigAsyncDeleteKeyData(impu,key) => sender ! SigAsyncDeleteDataResult(deleteUserKeyword(impu,key))
    case CheckConnection => diameterServer.checkForConnection
  }


  private def requestData(impu:String):Future[Option[RepoData]] = {
    val udr = makeCommonMessage(ShUDR,impu)
    udr add new AVP_UTF8String(ServiceIndication,TGPP,serviceIndication).setM
    val answerFuture = diameterServer.sendRequest(udr)
    answerFuture map { answer =>
      if(isSuccessfulAnswer(answer))  {
        val userDataAvp = answer.find(UserData,TGPP)
        if(userDataAvp == null) {
          log.debug("UserData is not in answer message, has no data before")
          Some(RepoData(-1,Map.empty[String,Int]))
        } else{
          val userData = new AVP_UTF8String(userDataAvp).queryValue()
          log.debug(s"UserData is $userData")
          //parse XML data
          parseRepoXml(userData)
        }
      } else{
        log.warning(s"Diameter:Requesting repo data from HSS failed for user $impu")
        None
      }
    }
  }

  private def parseRepoXml(data:String):Option[RepoData] = {
    try {
      val xml = XML.load(new StringReader(data))
      val seqNum = (xml \ "RepositoryData" \ "SequenceNumber").text.toInt
      val serviceData = (xml \ "RepositoryData" \ "ServiceData" ).text
      log.debug(s"Seq num is $seqNum, serviceData is $serviceData")
      val map = serviceData.parseJson.convertTo[Map[String,Int]]
      Some(RepoData(seqNum,map))
    } catch {
      case e:Exception => {log.error("Parsing repo data failed, {}",e); None}
    }
  }

  private def updateData(impu:String,data:Map[String,Int]):Future[Boolean] = {
    //get current data first
    requestData(impu).flatMap { o =>
      o match {
        case None => {
          Future{
            log.error("Updating failed because requesting failed")
            false
          }
        }
        case Some(repoData) => {
          val newSeq = repoData.seq + 1
          val newJson = data.toMap.toJson.compactPrint
          val newRepo =
            <Sh-Data>
              <RepositoryData>
                <ServiceIndication>{serviceIndication}</ServiceIndication>
                <SequenceNumber>{newSeq}</SequenceNumber>
                <ServiceData>{newJson}</ServiceData>
              </RepositoryData>
            </Sh-Data>
          val writer = new StringWriter()
          XML.write(writer,newRepo,"utf-8",true,null)
          writer.flush
          //make new repoData
          val pur = makeCommonMessage(ShPUR,impu)
          pur.add((new AVP_UTF8String(UserData,TGPP,writer.toString)).setM)

          val answerFuture = diameterServer.sendRequest(pur)
          answerFuture.map { a=>
            isSuccessfulAnswer(a)
          }
        }
      }
    }
  }

  private def addData(impu:String,data:Seq[String]):Future[Boolean] = {
    //get current data first
    requestData(impu).flatMap { o =>
      o match {
        case None => {
          Future{
            log.error("Updating failed because requesting failed")
            false
          }
        }
        case Some(repoData) => {
          val temp = collection.mutable.Map.empty[String,Int] ++ repoData.data
          for(newIn <- data) {
            val oldData = temp.getOrElse(newIn,0)
            temp.put(newIn,oldData+1)
          }
          updateData(impu,temp.toMap)
        }
      }
    }
  }

  private def deleteUserData(impu:String):Future[Boolean] = {
    requestData(impu).flatMap  { o =>
      o match {
        case None => {
          Future{
            log.error("Deleting failed because requesting failed")
            false
          }
        }
        case Some(repoData) => {
          if(repoData.seq < 0 ) Future{true}
          else  {
            val newSeq = repoData.seq + 1
            val newRepo =
              <Sh-Data>
                <RepositoryData>
                  <ServiceIndication>{serviceIndication}</ServiceIndication>
                  <SequenceNumber>{newSeq}</SequenceNumber>
                </RepositoryData>
              </Sh-Data>
            val writer = new StringWriter()
            XML.write(writer,newRepo,"utf-8",true,null)
            writer.flush
            //make new repoData
            val pur = makeCommonMessage(ShPUR,impu)
            pur.add((new AVP_UTF8String(UserData,TGPP,writer.toString)).setM)

            diameterServer.sendRequest(pur).map { a=>
              isSuccessfulAnswer(a)
            }
          }
        }
      }
    }
  }

  private def deleteUserKeyword(impu:String,keyword:String):Future[Boolean] = {
    requestData(impu).flatMap  { o =>
      o match {
        case None => {
          Future{
            log.error("Deleting keyword failed because requesting failed")
            false
          }
        }
        case Some(repoData) => {
          if(repoData.seq < 0 ) Future{true}
          else  {
            if(repoData.data.contains(keyword))
              updateData(impu,repoData.data - keyword)
            else
              Future {true}
          }
        }
      }
    }
  }


  private def isSuccessfulAnswer(answer:Message):Boolean = {
    if(answer != null) {
      val retCodeAvp = answer.find(ResultCode)
      if (retCodeAvp == null) {
        if(/*log.isDebugEnabled*/true)  {
          //grasp Experimental-Result
          val expResult = new AVP_Grouped(answer.find(ExperimentalResult))
          val expResultCode = expResult.queryAVPs().filter(t => t.code == ExperimentalResultCode)
          if(expResultCode.length == 1) {
            val code = (new AVP_Integer32(expResultCode(0))).queryValue()
            log.info("Experimental result code is {}",code)
          }
        }
        false
      }
      else {
        val retCode = (new AVP_Integer32(retCodeAvp)).queryValue()
        if(retCode == DiameterSuccess) {
          true
        } else  {
          log.info("Diameter result code {}",retCode)
          false
        }

      }
    } else
      false
  }

  private def makeCommonMessage(cmd:Int,impu:String):Message = {
    val message = new Message
    //Sh appid
    message.hdr.application_id = ShAppId
    message.hdr.command_code = cmd
    message.hdr.setRequest(true)
    message.hdr.setProxiable(true)

    //Session-Id avp, stubbed
    message add new AVP_UTF8String(SessionId,"session.poc.ericsson.se").setM

    //Vendor-Specific-Application-Id
    val vendorId = new AVP_Integer32(VendorId,TGPP).setM
    val authAppId = new AVP_Integer32(AuthAppId,ShAppId).setM
    message add new AVP_Grouped(VendorSpecificId,vendorId,authAppId).setM

    message add new AVP_UTF8String(OriginHost,originHost).setM
    message add new AVP_UTF8String(OriginRealm,originRealm).setM
    message add new AVP_UTF8String(DestinationRealm,destinationRealm).setM
    message add new AVP_Integer32(AuthSessState,1).setM
    message add new AVP_Grouped(UserId,TGPP,new AVP_UTF8String(PubId,TGPP,impu).setM).setM
    //service indication
    //message add new AVP_UTF8String(ServiceIndication,TGPP,serviceIndication).setM
    //RepositoryData
    message add new AVP_Integer32(DataRef,TGPP,0).setM
    message
  }
}
