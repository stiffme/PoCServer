package com.esipeng.content

/**
 * Created by esipeng on 9/16/2015.
 */
class ContentProviderStub extends IContentProvider{
  val stubDataFood = Seq[Note](
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","vegetable","vegetable content","vegetable"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream"),
    Note("/images/test.png","ice cream","ice cream content","icecream")
  )

  val stubDataHealth = Seq[Note](
    Note("/images/test.png","cancer","cancer content","cancer"),
    Note("/images/test.png","cold","cold content","cold")
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

  override def getAll(category:String,keywords: Seq[String]):collection.Seq[Note] = {
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

  def getWithKeywords(data:Seq[Note], keywords:Seq[String]):collection.Seq[Note] = {
    //dirty solution here, memory is not considered, but who cares?

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
    ret.toSeq
  }
}
