package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.DaoAll
import com.janrain.backplane.common.UserPassAuth
import com.janrain.backplane.server2.oauth2.model.{BusOwnerFields, BusOwner}

/**
 * @author Johnny Bufu
 */
trait BusOwnerDao extends DaoAll[BusOwner] with UserPassAuth[BusOwnerFields.EnumVal, BusOwner]
