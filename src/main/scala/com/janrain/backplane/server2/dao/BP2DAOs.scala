package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.redis.{Redis, MessageProcessorDaoSupport, RedisMessageDao}
import com.janrain.backplane.server2.oauth2.model._
import com.janrain.backplane.dao.{LegacyDaoForwarder, PasswordHasherDao, ExpiringDao}
import com.janrain.backplane.server2.model._
import com.janrain.backplane.server2.dao.redis.RedisBackplane2MessageDao
import scala.collection.JavaConversions._

/**
 * @author Johnny Bufu
 */
object BP2DAOs {

  val authSessionDao: AuthSessionDao = new RedisMessageDao[AuthSession]("bp2AuthSession:")
    with AuthSessionDao
    with ExpiringDao[AuthSession] {
    protected def instantiate(data: Map[_, _]) = new AuthSession(data.map(kv => kv._1.toString -> kv._2.toString ))
    val expireSeconds = 3600 // 1h
  }

  val authorizationRequestDao: AuthorizationRequestDao = new RedisMessageDao[AuthorizationRequest]("bp2AuthorizationRequest:")
    with AuthorizationRequestDao
    with ExpiringDao[AuthorizationRequest] {
    protected def instantiate(data: Map[_, _]) = new AuthorizationRequest(data.map(kv => kv._1.toString -> kv._2.toString ))

    val expireSeconds = 300 // 5min
  }

  val authorizationDecisionKeyDao: AuthorizationDecisionKeyDao = new RedisMessageDao[AuthorizationDecisionKey]("bp2AuthorizationDecisionKey:")
    with AuthorizationDecisionKeyDao
    with ExpiringDao[AuthorizationDecisionKey] {
    protected def instantiate(data: Map[_, _]) = new AuthorizationDecisionKey(data.map(kv => kv._1.toString -> kv._2.toString ))

    val expireSeconds = 300 // 5min
  }

  val busDao: BusDao = new RedisMessageDao[BusConfig2]("bp2Bus:") with BusDao
    with LegacyDaoForwarder[com.janrain.backplane2.server.config.BusConfig2, BusConfig2] {

    protected def instantiate(data: Map[_, _]) = new BusConfig2( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def retrieveByOwner(busOwner: String): List[BusConfig2] = {
      (super.retrieveByOwner(busOwner) ++ legacyDao.retrieveByOwner(busOwner).toList.map(new BusConfig2(_)))
      .toSet.toList // remove duplicates
    }

    override def delete(id: String): Boolean = {
      val busDeleteSuccess = super.delete(id)
      grantDao.deleteByBus(List(id)) // throws
      busDeleteSuccess
    }

    override def deleteByOwner(busOwner: String) {
      super.deleteByOwner(busOwner)
      legacyDao.deleteByOwner(busOwner)
    }

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getBusDao
  }

  val busOwnerDao: BusOwnerDao = new RedisMessageDao[BusOwner]("bp2BusOwner:") with BusOwnerDao
    with PasswordHasherDao[BusOwnerFields.EnumVal,BusOwner]
    with LegacyDaoForwarder[com.janrain.backplane2.server.config.User, BusOwner] {

    protected def instantiate(data: Map[_, _]) = new BusOwner( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def storeFromLegacy(convertedItem: BusOwner) {
      storeNoPwdHash(convertedItem)
    }

    override def delete(id: String): Boolean = {
      val busOwnerDeleteSuccess = super.delete(id)
      busDao.deleteByOwner(id) // throws if not success
      busOwnerDeleteSuccess
    }

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getBusOwnerDAO
  }

  val clientDao: ClientDao = new RedisMessageDao[Client]("bp2Client:") with ClientDao
    with PasswordHasherDao[ClientFields.EnumVal,Client]
    with LegacyDaoForwarder[com.janrain.backplane2.server.config.Client, Client] {

    protected def instantiate(data: Map[_, _]) = new Client( data.map( kv => kv._1.toString -> kv._2.toString ))

    override def storeFromLegacy(convertedItem: Client) {
      storeNoPwdHash(convertedItem)
    }

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getClientDAO
  }

  val grantDao: GrantDao = new RedisMessageDao[Grant2]("bp2Grant:") with GrantDao
    with LegacyDaoForwarder[com.janrain.backplane2.server.Grant, Grant2] {

    protected def instantiate(data: Map[_, _]) = new Grant2( data.map( kv => kv._1.toString -> kv._2.toString ))

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getGrantDao

    override def getByClientId(clientId: String): List[Grant2] = {
      (super.getByClientId(clientId) ++ legacyDao.getByClientId(clientId).toList.map(new Grant2(_)))
      .toSet.toList // remove duplicates
    }

    override def revokeBuses(grants: List[Grant2], buses: List[String]) = {
      super.revokeBuses(grants, buses) & legacyDao.revokeBuses(grants.map(_.asLegacy), buses)
    }

    override def deleteByBus(busesToDelete: List[String]) {
      super.deleteByBus(busesToDelete)
      legacyDao.deleteByBuses(busesToDelete)
    }
  }

  val tokenDao: TokenDao = new RedisMessageDao[Token]("bp2Token:") with TokenDao
    with LegacyDaoForwarder[com.janrain.backplane2.server.Token, Token] {

    protected def instantiate(data: Map[_, _]) = new Token( data.map( kv => kv._1.toString -> kv._2.toString ))

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getTokenDao
  }

  val channelDao: ChannelDao = new RedisMessageDao[Channel]("bp2Channel:") with ChannelDao
    with LegacyDaoForwarder[com.janrain.backplane2.server.Channel, Channel] {

    protected def instantiate(data: Map[_, _]) = new Channel( data.map( kv => kv._1.toString -> kv._2.toString ))

    val legacyDao = com.janrain.backplane2.server.dao.BP2DAOs.getChannelDao

    def getExpire(channelId: String) = Redis.readPool.withClient(_.ttl(getKey(channelId)).getOrElse(0L).toInt)
  }

  type BackplaneMessageDaoWithProcessor = Backplane2MessageDao with MessageProcessorDaoSupport[Backplane2MessageFields.EnumVal,Backplane2Message]

  val messageDao: BackplaneMessageDaoWithProcessor = RedisBackplane2MessageDao

}
