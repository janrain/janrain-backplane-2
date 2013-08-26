package com.janrain.backplane.server2.dao

import com.janrain.backplane.dao.DaoAll
import com.janrain.backplane.common.UserPassAuth
import com.janrain.backplane.server2.oauth2.model.{ClientFields, Client}

/**
 * @author Johnny Bufu
 */
trait ClientDao extends DaoAll[Client] with UserPassAuth[ClientFields.EnumVal, Client]
