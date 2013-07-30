package com.janrain.utils

import scala.io.Source
import akka.actor.{Actor, ActorSystem, Props}
import scalax.file.{FileSystem, Path}
import java.text.SimpleDateFormat
import java.util.Date
import org.apache.log4j.{Appender, Logger, FileAppender, PatternLayout}
import com.cloudera.flume.log4j.appender.FlumeLog4jAvroAppender
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

// A logger for analytics messages. Use method 'log' to send a message.
object AnalyticsLogger {
  val logger = Logger.getLogger(this.getClass)
  val hasShutdown = new AtomicBoolean()
  val anilogger = Logger.getRootLogger.getLoggerRepository.getLogger("analytics")

  val appender: Option[Appender] = {
    val hostport = "(.+):([0-9]+)".r
    val infoMsg = "Analytics logger output is sent to: "
    System.getProperty(BackplaneSystemProps.ANALYTICS_LOGGING) match {
      case "file" => {
        val filePath = sys.props("java.io.tmpdir") + "/flume.log"
        logger.info(infoMsg + filePath)
        Some(new FileAppender(new PatternLayout(), filePath))
      }
      case hostport(host, port) => {
        val a = new FlumeLog4jAvroAppender()
        a setName "ANALYTICS"
        a setHostname host
        a setPort port.toInt
        a setReconnectAttempts 1
        logger.info(infoMsg + host + ':' + port)
        Some(a)
      }
      case _ => {
        logger.info(infoMsg + "disabled")
        None
      }
    }
  }

  appender map anilogger.addAppender

  val fallback: Option[AnalyticsFallback] = appender map setupFallback

  def setupFallback(a: Any): AnalyticsFallback = {
    val propPath = System.getProperty(BackplaneSystemProps.ANALYTICS_FALLBACK_PATH)
    val propSize = System.getProperty(BackplaneSystemProps.ANALYTICS_FALLBACK_MAXSIZEMB)
    val path = if (propPath == null) "/tmp/backplane-analytics-fallback.log"
               else propPath
    logger.info("Analytics fallback file path set to " + path)
    val size = if (propSize == null) "10"
               else propSize
    logger.info("Analytics fallback file size set to " + size + "MB")

    new AnalyticsFallback(path, size.toLong * (1 << 20))
  }

  val system = ActorSystem("Analytics")
  val loggingActor = system.actorOf(Props(new AnalyticsActor(anilogger)), "analytics-actor")

  // Use this to check if analytics logging is enabled.
  val isEnabled = appender.isDefined

  def log(event_type: String, event_json: String) {
    log("backplane:" + event_type + " " + event_json)
  }

  // Log analytics event strings. Also accepts Iterator[String].
  // Event message has the form
  //    backplane:<event_type> <event_json>
  def log(msg: Any) {
    if (isEnabled) {
      msg match {
        case msgStr: String => loggingActor ! msgStr
        case msgIt: Iterator[_] => for (msgStr <- msgIt) loggingActor ! msgStr
        case x => sys.error("Invalid type: " ++ x.getClass().toString())
      }
    }
  }

  // Persist an untransmitted message if there is space in the dump file.
  def failed(msg: String) {
    fallback.get.failed(msg)
  }

  // Feed untransmitted log messages back in the actor's mailbox.
  def replay() {
    fallback.get.replay() { msgs: Iterator[_] => log(msgs) }
  }

  @PreDestroy
  def shutdown() {
    if (!hasShutdown.getAndSet(true)) {
        system.shutdown
    }
  }
}

// An actor through which analytics messages are relayed so they are performed asynchronously.
class AnalyticsActor(analytics: Logger) extends Actor {
  val logger = Logger.getLogger(this.getClass)

  // XXX This is a hack until pattern layouts are properly handled by the Flume Avro appender (and we update the jar).
  val dateFormat = new SimpleDateFormat("MMM dd HH:mm:ss.SSS ") // With a trailing space!

  def receive = {
    case msg: String =>
      try {
        // Send the message.
        analytics.info(dateFormat.format(new Date()) ++ msg)
        // Send previously failed messages, if any.
        AnalyticsLogger.replay()
      } catch {
        case e: Exception =>
          logger.error("Analytics logging exception: ", e)
          logger.debug("Failed analytics event: " ++ msg)
          AnalyticsLogger.failed(msg)
      }
  }
}

// A handler for untransmitted analytics messages.
//   - Stores messages in a temporary file.
//   - Replays failed messages on demand.
class AnalyticsFallback(path: String, maxSize: Long) {
  def fsPath: Path = FileSystem.default.fromString(path)

  def appendln(msg: String) {
    fsPath.append(msg ++ "\n")
  }

  def failed(msg: String) {
    if (maxSize != 0) {
      if (maxSize < 0) appendln(msg)
      else {
        fsPath.size match {
          case Some(size) => if (size < maxSize) appendln(msg)
          case None => appendln(msg)
        }
      }
    }
  }

  def replay() (fn: Iterator[_] => Unit) = {
    if (fsPath.exists) {
      fn(Source.fromFile(path).getLines())
      fsPath.delete(true)
    }
  }
}

// A companion class (so that it can be "dependency injected" by Java).
class AnalyticsLogger {}
