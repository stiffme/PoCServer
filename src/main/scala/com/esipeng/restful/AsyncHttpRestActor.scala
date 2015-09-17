package com.esipeng.restful

import akka.actor.{ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.esipeng.content.IContentProvider
import com.esipeng.content.NoteJson._
import com.esipeng.diameter._
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing.HttpServiceActor

import scala.concurrent.duration._
import scala.util.{Failure, Success}
/**
 * Created by esipeng on 9/2/2015.
 * Restful service interface
 * GET /api/food|health get food info without SSO
 * GET /api/food|health/$IMPU get food info with SSO
 * POST /api/$IMPU add keyword JSON object
 *
 * GET /api/shopping redirect to frontpage
 * GET /api/shopping/$IMPU redirect to frontpage using keywords of IMPU
 *
 * GET /api/keywords/$IMPU get all keywords of impu
 * DELETE /api/keywords/$IMPU/$keyword delete one key
 * DELETE /api/keywords/$IMPU delete whole keywords!
 */
class AsyncHttpRestActor(diameter:ActorRef,dataRepo:IContentProvider) extends HttpServiceActor with ActorLogging{
  implicit val timeout = Timeout(1 second)
  implicit val executor = context.system.dispatcher

  val getList = { //implements GET /api/food/
    get {
      path("api" / """(food|health)""".r ) { category =>
        pathEndOrSingleSlash  {
          complete(dataRepo.getAll(category))
        }
      }
    }
  }



  val getListSSO = { //implements GET /api/food/$IMPU
    get {
      path("api" / """(food|health)""".r  / Segment){ (category,userid) =>
        pathEndOrSingleSlash  {
          val fut = diameter.ask(SigAsyncRequestData(userid)).mapTo[SigAsyncRequestDataResult]
          onComplete( fut.flatMap( t=> t.repoData) ) {
            case Success(data) => {
              data match {
                case Some(d) => {
                  //keyworkds is in d.data
                  val keys:Seq[String] = d.data.toList.sortBy( _._2).map( _._1).reverse
                  complete(dataRepo.getAll("food",keys))
                }
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

  val getSSO = { //implements GET /api//$IMPU
    get {
      path("api"   / Segment){ userid =>
        pathEndOrSingleSlash  {
          val fut = diameter.ask(SigAsyncRequestData(userid)).mapTo[SigAsyncRequestDataResult]
          onComplete( fut.flatMap( t=> t.repoData) ) {
            case Success(data) => {
              data match {
                case Some(d) => {

                  complete(d.data)
                }
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

  val postRoute = { //POST /api/$IMPU add keyword JSON object
    post {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          entity(as[Seq[String]]) { seq =>
            val fut = diameter.ask(SigAsyncAddData(userid,seq)).mapTo[SigAsyncAddDataResult]

            onComplete( fut.flatMap( t=> t.success)) {
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


  val deleteKeyRoute = { //DELETE /api/$IMPU/$keyword delete keywords
    delete {
      path("api" / Segment / Segment) { (userid,key) =>
        pathEndOrSingleSlash  {
          val fut = diameter.ask(SigAsyncDeleteKeyData(userid,key)).mapTo[SigAsyncDeleteDataResult]

          onComplete( fut.flatMap( t=> t.success)) {
            case Failure(exe) => {
              log.error("Deleting key data {} from Diameter layer failed, {}",userid,exe)
              complete(StatusCodes.InternalServerError,"Deleting key data from Diameter layer failed")
            }
            case Success(r) =>  {
              if(r) complete("OK")
              else  {
                log.error("Deleting key data {} from Diameter layer failed, diameter layer returned false",userid)
                complete(StatusCodes.InternalServerError,"Deleting key data from Diameter layer failed")
              }
            }
          }
        }
      }
    }
  }

  val deleteRoute = { //DELETE /api/$IMPU delete keywords
    delete {
      path("api" / Segment) { userid =>
        pathEndOrSingleSlash  {
          val fut = diameter.ask(SigAsyncDeleteData(userid)).mapTo[SigAsyncDeleteDataResult]

          onComplete( fut.flatMap( t=> t.success)) {
            case Failure(exe) => {
              log.error("Deleting all key data {} from Diameter layer failed, {}",userid,exe)
              complete(StatusCodes.InternalServerError,"Deleting all key data from Diameter layer failed")
            }
            case Success(r) =>  {
              if(r) complete("OK")
              else  {
                log.error("Deleting all key data {} from Diameter layer failed, diameter layer returned false",userid)
                complete(StatusCodes.InternalServerError,"Deleting all key data from Diameter layer failed")
              }
            }
          }
        }
      }
    }
  }

  def receive = runRoute(getList ~ getListSSO ~ postRoute ~ deleteKeyRoute ~ deleteRoute ~ getSSO)
}
