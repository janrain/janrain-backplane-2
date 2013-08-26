package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.DaoAll
import com.janrain.backplane2.server.{GrantBuilder, Scope}
import scala.collection.JavaConversions._
import com.janrain.util.Loggable
import com.janrain.backplane.server2.oauth2.model.{GrantFields, Grant2}
import com.janrain.backplane.server2.model.Backplane2MessageFields

/**
 * @author Johnny Bufu
 */
trait GrantDao extends DaoAll[Grant2] with Loggable {

  def getByClientId(clientId: String): List[Grant2] = getAll.filter(_.get(GrantFields.ISSUED_TO_CLIENT).exists(_ == clientId))

  abstract override def update(grant: Grant2) = {
    super.update(grant)
    revokeTokens(Set(grant.id))
  }

  abstract override def update(grants: Grant2*) = {
    val grantUpdateResult = super.update(grants: _*)
    revokeTokens( grants.map(_.id).toSet)
    grantUpdateResult
  }

  abstract override def delete(grantId: String) = {
    revokeTokens(Set(grantId))
    super.delete(grantId)
  }

  abstract override def delete(grantIds: String*) = {
    revokeTokens( grantIds.toSet )
    super.delete(grantIds: _*)
  }

  /**
   * Revokes buses across the provided grants.
   * Not atomic, best effort.
   * Stops on first error and reports error, even though some grants may have been updated.
   *
   * @return true if any of the existing grants were updated, false if nothing was updated
   */
  def revokeBuses(grants: List[Grant2], buses: List[String]): Boolean = {
    val busesToRevoke = new Scope(Scope.getEncodedScopesAsString(Backplane2MessageFields.BUS, seqAsJavaList(buses)))
    val updatedGrantIds = grants.withFilter(revokeBusesFromGrant(_, busesToRevoke)).map(_.id).toSet
    revokeTokens(updatedGrantIds)
    ! updatedGrantIds.isEmpty
  }

  def deleteByBus(busesToDelete: List[String]) {
    val deleteBusesScope: Scope = new Scope(Scope.getEncodedScopesAsString(Backplane2MessageFields.BUS, seqAsJavaList(busesToDelete)))
    getAll.foreach(grant => {
      Option(grant.getAuthorizedScope.getScopeFieldValues(Backplane2MessageFields.BUS))
      .map(_.foreach(bus =>
        if (busesToDelete.contains(bus)) revokeBusesFromGrant(grant, deleteBusesScope)
      ))
    })
  }

  private def revokeBusesFromGrant(grant: Grant2, busesToRevoke: Scope): Boolean = {
    val grantScope = grant.getAuthorizedScope
    val updatedScope = Scope.revoke(grantScope, busesToRevoke)
    if (updatedScope == grantScope) return false

    if (!updatedScope.isAuthorizationRequired) {
      logger.info("Revoked all buses from grant: " + grant.id)
      delete(grant.id)
    } else {
      val updated: Grant2 = new GrantBuilder(grant, grant.getState).scope(updatedScope).buildGrant
      update(grant, updated)
      logger.info("Buses updated updated for grant: " + updated.id + " remaining scope: '" + updated.getAuthorizedScope + "'")
    }
    true
  }

  private def revokeTokens(grantIds: Set[String]) {
    if ( ! grantIds.isEmpty ) {
      BP2DAOs.tokenDao.getAll
        .withFilter( t => ! (t.backingGrants.toSet & grantIds).isEmpty)
        .foreach(t => BP2DAOs.tokenDao.delete(t.id))
    }
  }

}
