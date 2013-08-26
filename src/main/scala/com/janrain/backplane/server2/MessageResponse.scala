package com.janrain.backplane.server2

import scala.annotation.tailrec
import com.janrain.backplane2.server.Scope
import java.util.Date
import com.janrain.backplane.server2.model.Backplane2Message
import org.apache.commons.lang.StringUtils
import java.util
import com.janrain.backplane.server2.dao.BP2DAOs
import scala.collection.JavaConversions._

/**
 * @author Johnny Bufu
 */
object MessageResponse {

  def scalaObject = this

  def apply( serverName: String, privileged: Boolean,
             scope: Scope, lastMessageId: String, sleepMillis: Long, sleepUntil: Date) = {
    (response(serverName, privileged) _)
    .tupled(messageLoop(scope, lastMessageId, sleepMillis, sleepUntil))
  }

  @tailrec
  def messageLoop(scope: Scope, lastMessageId: String, sleepMillis: Long, sleepUntil: Date): (List[Backplane2Message], Boolean, Option[String]) = {
    val (messages, isMore, lastIdChecked) = BP2DAOs.messageDao.retrieveMessagesPerScope(scope, lastMessageId)
    if ( ! messages.isEmpty || new Date().after(sleepUntil)) {
      (messages, isMore, lastIdChecked)
    } else {
      try {
        Thread.sleep(sleepMillis)
      } catch {
        case e: InterruptedException => // ignore
      }
      messageLoop(scope, lastIdChecked.getOrElse(""), sleepMillis, sleepUntil)
    }
  }

  def response( serverName: String, privileged: Boolean )
              ( messages: List[Backplane2Message], more: Boolean, lastMessageId: Option[String] ) = {
    // todo: proper, not abandonware json library with support for scala types
    val frames = messages.map(_.asFrame(serverName, privileged))
    val lastMessageIdParam = lastMessageId.map(last => if (!StringUtils.isBlank(last)) "?since=" + last else "").getOrElse("")
    val messagesResponse: java.util.Map[String,Object] = new util.HashMap[String, Object]
    messagesResponse.put("nextURL", "https://" + serverName + "/v2/messages" + lastMessageIdParam)
    messagesResponse.put("moreMessages", java.lang.Boolean.valueOf(more))
    messagesResponse.put("messages", seqAsJavaList(frames.map(mapAsJavaMap)))
    (messagesResponse, messages)
  }

}
