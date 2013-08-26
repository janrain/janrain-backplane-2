package com.janrain.backplane.server1.dao

import com.janrain.backplane.dao.redis.RedisMessageDao
import com.janrain.backplane.server1.model._
import com.janrain.backplane.dao.{LegacyDaoForwarder, PasswordHasherDao}
import com.janrain.backplane.server1.dao.redis.RedisBackplane1MessageDao
import scala.collection.JavaConversions._
import com.janrain.util.Loggable
import com.janrain.backplane.config.dao.ConfigDAOs
import com.janrain.backplane.config.model.ServerConfigFields
import com.janrain.backplane.server

/**
 * @author Johnny Bufu
 */
object BP1DAOs extends Loggable {

  val userDao: BusUserDao = new RedisMessageDao[BusUser]("bp1BusUser:") with BusUserDao
    with PasswordHasherDao[BusUserFields.EnumVal,BusUser]
    with LegacyDaoForwarder[com.janrain.backplane2.server.config.User, BusUser] {

    protected def instantiate(data: Map[_, _]) = new BusUser( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def storeFromLegacy(convertedItem: BusUser) {
      storeNoPwdHash(convertedItem)
    }

    val legacyDao = com.janrain.backplane.server.redisdao.BP1DAOs.getUserDao
  }

  val busDao: BusDao = new RedisMessageDao[BusConfig1]("bp1Bus:") with BusDao
    with LegacyDaoForwarder[com.janrain.backplane.server.BusConfig1, BusConfig1] {

    protected def instantiate(data: Map[_, _]) = new BusConfig1( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def instantiateFromLegacy(legacyItem: server.BusConfig1) = instantiate(BusConfig1.fromLegacy(legacyItem))

    val legacyDao = com.janrain.backplane.server.redisdao.BP1DAOs.getBusDao
  }

  //type BackplaneMessageDaoWithProcessor = Backplane1MessageDao with MessageProcessorDaoSupport[Backplane1MessageFields.EnumVal,Backplane1Message]

  // todo: change RedisBackplane1MessageDao to object after legacy dao is removed
  val messageDao: Backplane1MessageDao = new RedisBackplane1MessageDao {

    val legacyMessageDao = com.janrain.backplane.server.redisdao.BP1DAOs.getMessageDao

    override def messageCount(channel: String) = {
      if (isUseNewDao) super.messageCount(channel)
      else legacyMessageDao.getMessageCount(null, channel)
    }

    override def retrieveMessagesByBus(bus: String, since: String, sticky: String) = {
      // todo: fire off these two in parallel
      val newDaoMsgs = super.retrieveMessagesByBus(bus, since, sticky)
      val legacyDaoMsgs = legacyMessageDao.getMessagesByBus(bus, since, sticky).map(new Backplane1Message(_)).toList
      checkCompare("bus: %s, since: %s, sticky: %s".format(bus,since,sticky), legacyDaoMsgs, newDaoMsgs)
    }

    override def retrieveMessagesByChannel(channel: String, since: String, sticky: String) = {
      val newDaoMsgs = super.retrieveMessagesByChannel(channel, since, sticky)
      val legacyDaoMsgs = legacyMessageDao.getMessagesByChannel(null, channel, since, sticky).map(new Backplane1Message(_)).toList
      checkCompare("channel: %s, since: %s, sticky: %s".format(channel,since,sticky), legacyDaoMsgs, newDaoMsgs)
    }

    private def checkCompare(queryString: String, legacyDaoMsgs: List[Backplane1Message], newDaoMsgs: List[Backplane1Message]): List[Backplane1Message] = {
      // the two lists may have extra/missing messages at the end, since they were obtained from separate DAO calls
      // but they should most of the time "zip" with identical messages (less the few trailing ones)
      // okay (but not very likely) for the heads to be different if the head message(s) just expired in between the two DAO calls
      val id1s = legacyDaoMsgs.map(_.id)
      val id2s = newDaoMsgs.map(_.id)
      val identical = (id1s zip id2s).foldLeft(true) {
        case (acc, (id1, id2)) => acc && (id1 == id2)
      }

      (identical, isUseNewDao) match {
        case (false, true) =>
          logger.warn("%s query generated different legacy vs new DAO results: [%s] vs [%s]".format(queryString, id1s.mkString(" "), id2s.mkString(" ")))
        case (false, false) =>
          logDebug("%s query generated different legacy vs new DAO results: [%s] vs [%s]".format(queryString, id1s.mkString(" "), id2s.mkString(" ")))
        case _ =>
      }

      if ( isUseNewDao ) newDaoMsgs
      else legacyDaoMsgs
    }

    private def isUseNewDao: Boolean = ConfigDAOs.serverConfigDao.oneServerConfig
      .flatMap(_.get(ServerConfigFields.BP1_MESSAGES_USE_NEW_DAO))
      .exists(_ == true.toString)
  }

  def asScalaImmutableMap(javaMap: java.util.Map[String,String]) = javaMap.toMap

}
