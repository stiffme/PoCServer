package com.esipeng.content

import java.io.{File, FileFilter, FileInputStream}

import org.slf4j.LoggerFactory

import scala.xml.XML

/**
 * Created by esipeng on 9/21/2015.
 */
class DirectoryContentProvider(val contentDir:String) extends ContentProviderStub(contentDir){
  final val logger = LoggerFactory.getLogger(classOf[DirectoryContentProvider])
  final var foodContent:Seq[Note] = _
  final var healthContent:Seq[Note] = _


  override def init(): Unit = {
    foodContent = initDirectory(new File(contentDir + File.separatorChar + "food"))
    healthContent = initDirectory(new File(contentDir + File.separatorChar + "health"))
  }

  def initDirectory(dir:File):Seq[Note] = {
    if(dir.exists() && dir.isDirectory) {
      val files = dir.listFiles(new FileFilter {
        override def accept(pathname: File): Boolean = pathname.exists() && pathname.canRead() && pathname.isFile() && pathname.getPath.endsWith(".xml")
      })
      val ret = collection.mutable.ListBuffer.empty[Note]
      for( k <- files)  {
        val xmlFile = XML.load(new FileInputStream(k))
        val head = (xmlFile \ "head").text
        val image = (xmlFile \ "image").text
        val content = (xmlFile \ "content").text
        val keyword = (xmlFile \ "keyword").text
        logger.debug("File {} Adding head {}, image {}, content {}, keyword {}",k.getPath,head,image,content,keyword)
        val note = Note(image,head,content,keyword)
        ret += note
      }

      ret.toSeq
    } else{
      Seq.empty[Note]
    }
  }

  override def getAll(category:String): Seq[Note] = {
    if("food".equals(category))
      foodContent
    else if("health".equals(category))
      healthContent
    else
      Seq.empty[Note]
  }
}
