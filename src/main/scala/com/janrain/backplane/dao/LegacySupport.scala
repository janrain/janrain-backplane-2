package com.janrain.backplane.dao

import com.janrain.commons.supersimpledb.message.NamedMap
import scala.collection.JavaConversions._

/**
 * @author Johnny Bufu
 */
trait LegacySupport[LT <: NamedMap] {

  def asLegacy: LT

}

object LegacySupport {

  def fromLegacy(javaData: java.util.Map[String,String]) = javaData.toMap.map {
    case (k,v) => k.toLowerCase -> v
  }

  def toLegacy(scalaData: Map[String,String]) = mapAsJavaMap(scalaData.map {
    case (k,v) => k.toUpperCase -> v
  })
}