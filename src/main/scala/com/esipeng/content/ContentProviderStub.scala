package com.esipeng.content

/**
 * Created by esipeng on 9/16/2015.
 */
class ContentProviderStub(val contentPath:String) extends IContentProvider{
  val stubDataFood = Seq[Note](
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","vegetable","vegetable content",Set[String]("vegetable")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream")),
    Note("/images/test.png","ice cream","ice cream content",Set[String]("icecream"))
  )

  val stubDataHealth = Seq[Note](
    Note("/images/test.png","cancer","cancer content",Set[String]("cancer")),
    Note("/images/test.png","cold","cold content",Set[String]("cold"))
  )

  override def init(): Unit = {
    //stub , do nothing in init.
  }

  override def getAll(category:String): Seq[Note] = {
    if("food".equals(category))
       stubDataFood
    else if("health".equals(category))
       stubDataHealth
    else
      Seq.empty[Note]
  }

  override def getAll(category:String,keywords: Map[String,Int]):collection.Seq[Note] = {
    val data = {
      if("food".equals(category))
        stubDataFood
      else if("health".equals(category))
        stubDataHealth
      else
        Seq.empty[Note]
    }

    getWithKeywords(data,keywords)
  }

  def getWithKeywords(data:Seq[Note], keywords:Map[String,Int]):collection.Seq[Note] = {
    //dirty solution here, memory is not considered, but who cares?

    val ret = collection.mutable.HashMap.empty[Note,Int]
    for( n <- data) {
      var weight:Int = 0
      for(k <- n.keyword) {
        weight += keywords.getOrElse(k,0)
      }
      ret += ( n -> weight)
    }
    ret.toList.sortBy(_._2).map(_._1).reverse
    /*
    val left = collection.mutable.ListBuffer.empty[Note] ++ data
    val ret = collection.mutable.ListBuffer.empty[Note]

    for(k <- keywords)  {
      for(l <- left)  {
        if( k.equals(l.keyword) ) {
          left -= l
          ret += l
        }
      }
    }

    ret ++= left
    ret.toSeq*/
  }
}
