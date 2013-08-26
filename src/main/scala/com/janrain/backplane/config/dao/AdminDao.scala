package com.janrain.backplane.config.dao

import com.janrain.backplane.dao.Dao
import com.janrain.backplane.config.model.{AdminFields, Admin}
import com.janrain.backplane.common.UserPassAuth

/**
 * @author Johnny Bufu
 */
trait AdminDao extends Dao[Admin] with UserPassAuth[AdminFields.EnumVal, Admin]
