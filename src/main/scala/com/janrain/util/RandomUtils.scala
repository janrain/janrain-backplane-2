package com.janrain.util

object RandomUtils {

  def randomString(length: Int) = {
    val randomBytes = new Array[Byte](length)
    random nextBytes randomBytes

    new String(randomBytes.map(b => base62(scala.math.abs(b % base62.length))))
  }

  private val random: java.util.Random = new java.security.SecureRandom

  // the base64 character set per RFC 4648 with last two members '-' and '_' removed due to possible
  // compatibility issues.
  private val base62: Array[Char] =
    Array('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
      'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
      'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
}