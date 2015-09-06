package com.esipeng.restful

import akka.actor.{ActorLogging, ActorRef}
import akka.util.Timeout
import com.esipeng.diameter._
import spray.http.StatusCodes
import spray.routing.HttpServiceActor
import scala.concurrent.duration._
import akka.pattern.ask
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import scala.util.{Success, Failure}


/**
 * Created by esipeng on 9/2/2015.
 */
class HttpRestActor(diameter:ActorRef) extends HttpServiceActor with ActorLogging{
  implicit val timeout = Timeout(1 second)
  implicit val executor = context.system.dispatcher

  val getRoute = {
    get {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          onComplete( diameter.ask(SigRequestData(userid)).mapTo[SigRequestDataResult] ) {
            case Failure(ex) => {
              log.warning("Requesting data from Diameter layer failed, {}",ex)
              complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
            }
            case Success(data) => {
              data.repoData match {
                case Some(d) => complete(d.data)
                case None => {
                  log.warning("Requesting data from Diameter layer failed, diameter layer returned None")
                  complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
                }
              }
            }
          }
        }
      }
    }
  }

  val deleteRoute = {
    delete {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          onComplete( diameter.ask(SigDeleteData(userid)).mapTo[SigDeleteDataResult] ) {
            case Failure(ex) => {
              log.warning("Deleting data from Diameter layer failed, {}",ex)
              complete(StatusCodes.InternalServerError,"Deleting data from Diameter layer failed")
            }
            case Success(r) => {
              if(r.success) complete("OK")
              else  {
                log.warning("Deleting data from Diameter layer failed, diameter layer returned false")
                complete(StatusCodes.InternalServerError,"Deleting data from Diameter layer failed")
              }
            }
          }
        }
      }
    }
  }

  val postRoute = {
    post {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          entity(as[Map[String,Int]]) { map =>
            onComplete(diameter.ask(SigUpdateData(userid,map)).mapTo[SigUpdateDataResult])  {
              case Failure(ex) => {
                log.warning("Updating data from Diameter layer failed,{}",ex)
                complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
              }
              case Success(r) => {
                if(r.success) complete("OK")
                else  {
                  log.warning("updating data from Diameter layer failed, diameter layer returned false")
                  complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
                }
              }
            }
          }
        }
      }
    }
  }

  def receive = runRoute( getRoute ~ postRoute ~ deleteRoute)
}
