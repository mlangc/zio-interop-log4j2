package com.github.mlangc.zio.interop.log4j2

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.github.ghik.silencer.silent
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.spi.ReadOnlyThreadContextMap
import org.apache.logging.log4j.spi.ThreadContextMap
import org.apache.logging.log4j.util.BiConsumer
import org.apache.logging.log4j.util.ReadOnlyStringMap
import org.apache.logging.log4j.util.StringMap
import org.apache.logging.log4j.util.TriConsumer
import zio.FiberRef
import zio.UIO
import zio.ZIO

import scala.collection.JavaConverters._

@silent("JavaConverters")
class FiberAwareThreadContextMap extends ThreadContextMap with ReadOnlyThreadContextMap {

  import FiberAwareThreadContextMap.threadLocal

  def clear(): Unit = threadLocal.remove()
  def containsKey(key: String): Boolean = threadLocal.get().contains(key)
  def get(key: String): String = threadLocal.get().get(key).orNull
  def getCopy: util.Map[String, String] = new util.HashMap[String, String](mapAsJavaMap(threadLocal.get()))

  def getImmutableMapOrNull: util.Map[String, String] = threadLocal.get() match {
    case map if map.nonEmpty => mapAsJavaMap(map)
    case _ => null
  }

  def isEmpty: Boolean = threadLocal.get().isEmpty

  def put(key: String, value: String): Unit =
    update(_ + (key -> value))

  def remove(key: String): Unit =
    update(_ - key)

  def getReadOnlyContextData: StringMap =
    new StringMap {
      private val underlying = threadLocal.get()

      def clear(): Unit = throw new UnsupportedOperationException()
      def freeze(): Unit = ()
      def isFrozen: Boolean = true
      def putAll(source: ReadOnlyStringMap): Unit = throw new UnsupportedOperationException()
      def putValue(key: String, value: Any): Unit = throw new UnsupportedOperationException()
      def remove(key: String): Unit = throw new UnsupportedOperationException()
      def toMap: util.Map[String, String] = new util.HashMap[String, String](mapAsJavaMap(underlying))
      def containsKey(key: String): Boolean = underlying.contains(key)

      def forEach[V](action: BiConsumer[String, _ >: V]): Unit =
        underlying.foreach { case (k, v) => action.accept(k, v.asInstanceOf[V]) }

      def forEach[V, S](action: TriConsumer[String, _ >: V, S], state: S): Unit =
        underlying.foreach { case (k, v) => action.accept(k, v.asInstanceOf[V], state) }

      def getValue[V](key: String): V = underlying.get(key).orNull.asInstanceOf[V]
      def isEmpty: Boolean = underlying.isEmpty
      def size(): Int = underlying.size
    }

  private def update(f: Map[String, String] => Map[String, String]): Unit =
    threadLocal.set(f(threadLocal.get()))
}

object FiberAwareThreadContextMap {

  class InitializationError private[FiberAwareThreadContextMap](message: String) extends RuntimeException(message)

  @volatile
  private var threadLocal = new ThreadLocal[Map[String, String]] {
    override def initialValue(): Map[String, String] = Map.empty
  }

  private val initialized = new AtomicBoolean(false)

  /**
   * Makes sure that fiber aware MDC logging is properly initialized
   */
  def assertInitialized: UIO[Unit] = {
    ZIO.whenM(UIO(!initialized.get())) {
      val assertProperlyInitialized =
        ZIO.whenM(UIO(!ThreadContext.getThreadContextMap.isInstanceOf[FiberAwareThreadContextMap])) {
          ZIO.die {
            new InitializationError(
              s"""Could not initialize ZIO fiber aware MDC logging for Log4j 2:
                 |  Please try starting the JVM with -D${SystemProperty.ThreadContextMap.key}=${SystemProperty.ThreadContextMap.value}
                 |  or make sure that ${classOf[FiberAwareThreadContextMap].getSimpleName}#assertInitialized is executed *before* Log4j 2 has been initialized.
                 |""".stripMargin)
          }
        }

      for {
        _ <- UIO(System.setProperty(SystemProperty.ThreadContextMap.key, SystemProperty.ThreadContextMap.value))
        _ <- assertProperlyInitialized
        combineContexts = (first: Map[String, String], last: Map[String, String]) => first ++ last
        fiberRef <- FiberRef.make(Map.empty[String, String], combineContexts)
        fiberLocal <- fiberRef.unsafeAsThreadLocal
        set <- UIO(initialized.compareAndSet(false, true))
        _ <- ZIO.when(set)(UIO { threadLocal = fiberLocal })
      } yield ()
    }
  }
}
