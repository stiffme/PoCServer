package com.esipeng.restful

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.esipeng.diameter._
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.routing.HttpServiceActor

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Created by esipeng on 9/2/2015.
 */
class AsyncHttpRestActor(diameter:ActorRef) extends HttpServiceActor with ActorLogging{
  implicit val timeout = Timeout(1 second)
  implicit val executor = context.system.dispatcher

  val getRoute = {
    get {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          onComplete( diameter.ask(SigAsyncRequestData(userid)).mapTo[SigAsyncRequestDataResult] ) {
            case Failure(ex) => {
              log.error("Requesting data {} from Diameter layer failed, {}",userid,ex)
              complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
            }
            case Success(dataFuture) => {
              onComplete(dataFuture.repoData)  {
                case Failure(exe) => {
                  log.error("Requesting data {} from Diameter layer failed, {}",userid,exe)
                  complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
                }
                case Success(data) => {
                  data match {
                    case Some(d) => complete(d.data)
                    case None => {
                      log.error("Requesting data {} from Diameter layer failed, diameter layer returned None",userid)
                      complete(StatusCodes.InternalServerError,"Requesting data from Diameter layer failed")
                    }
                  }
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
          onComplete( diameter.ask(SigAsyncDeleteData(userid)).mapTo[SigAsyncDeleteDataResult] ) {
            case Failure(ex) => {
              log.error("Deleting data {} from Diameter layer failed, {}",userid,ex)
              complete(StatusCodes.InternalServerError,"Deleting data from Diameter layer failed")
            }
            case Success(rFuture) => {
              onComplete(rFuture.success) {
                case Failure(exe) => {
                  log.error("Deleting data {} from Diameter layer failed, {}",userid,exe)
                  complete(StatusCodes.InternalServerError,"Deleting data from Diameter layer failed")
                }
                case Success(r) =>  {
                  if(r) complete("OK")
                  else  {
                    log.error("Deleting data {} from Diameter layer failed, diameter layer returned false",userid)
                    complete(StatusCodes.InternalServerError,"Deleting data from Diameter layer failed")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val putRoute = {
    put {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          entity(as[Map[String,Int]]) { map =>
            onComplete( diameter.ask(SigAsyncUpdateData(userid,map)).mapTo[SigAsyncUpdateDataResult] ) {
              case Failure(ex) => {
                log.error("Updating data {} from Diameter layer failed, {}",userid,ex)
                complete(StatusCodes.InternalServerError,"Updating data from Diameter layer failed")
              }
              case Success(rFuture) => {
                onComplete(rFuture.success) {
                  case Failure(exe) => {
                    log.error("Updating data {} from Diameter layer failed, {}",userid,exe)
                    complete(StatusCodes.InternalServerError,"Updating data from Diameter layer failed")
                  }
                  case Success(r) =>  {
                    if(r) complete("OK")
                    else  {
                      log.error("Updating data {} from Diameter layer failed, diameter layer returned false",userid)
                      complete(StatusCodes.InternalServerError,"Updating data from Diameter layer failed")
                    }
                  }
                }
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
          entity(as[Seq[String]]) { seq =>
            onComplete( diameter.ask(SigAsyncAddData(userid,seq)).mapTo[SigAsyncAddDataResult] ) {
              case Failure(ex) => {
                log.error("Adding data {} from Diameter layer failed, {}",userid,ex)
                complete(StatusCodes.InternalServerError,"Adding data from Diameter layer failed")
              }
              case Success(rFuture) => {
                onComplete(rFuture.success) {
                  case Failure(exe) => {
                    log.error("Adding data {} from Diameter layer failed, {}",userid,exe)
                    complete(StatusCodes.InternalServerError,"Adding data from Diameter layer failed")
                  }
                  case Success(r) =>  {
                    if(r) complete("OK")
                    else  {
                      log.error("Adding data {} from Diameter layer failed, diameter layer returned false",userid)
                      complete(StatusCodes.InternalServerError,"Adding data from Diameter layer failed")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def receive = runRoute( getRoute ~ putRoute ~ deleteRoute ~ postRoute)
}
