package com.esipeng.content

import spray.json.DefaultJsonProtocol

/**
 * Created by esipeng on 9/16/2015.
 */

case class Note(image:String,head:String,content:String,keyword:String)
object NoteJson extends DefaultJsonProtocol {
  implicit val noteJsonConv = jsonFormat4(Note)
}


trait IContentProvider  {
  def init():Unit
  def getAll(category:String):collection.Seq[Note]
  def getAll(category:String,keywords:Seq[String]):collection.Seq[Note]
}

