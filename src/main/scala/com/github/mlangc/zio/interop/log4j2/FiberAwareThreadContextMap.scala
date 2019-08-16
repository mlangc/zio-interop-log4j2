package com.github.mlangc.zio.interop.log4j2

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.github.ghik.silencer.silent
import org.apache.logging.log4j.spi.ThreadContextMap
import scala.collection.JavaConverters._

import zio.FiberRef

@silent("discarded non-Unit")
class FiberAwareThreadContextMap extends ThreadContextMap {
  import FiberAwareThreadContextMap._

  def clear(): Unit = {
    initialContext.set(Map.empty)

    if (fiberAwareContext != null)
      fiberAwareContext.set(Map.empty)
  }

  def containsKey(key: String): Boolean = {
    (fiberAwareContext != null && fiberAwareContext.get().contains(key)) || initialContext.get().contains(key)
  }

  def get(key: String): String = {
    if (fiberAwareContext != null) fiberAwareContext.get().get(key).orNull
    else initialContext.get().get(key).orNull
  }

  def getCopy: util.Map[String, String] = {
    if (fiberAwareContext != null) new util.HashMap(fiberAwareContext.get().asJava)
    else new util.HashMap(initialContext.get().asJava)
  }

  def getImmutableMapOrNull: util.Map[String, String] = {
    if (fiberAwareContext != null) fiberAwareContext.get().asJava
    else initialContext.get().asJava
  }

  def isEmpty: Boolean = {
    if (fiberAwareContext != null) fiberAwareContext.get().isEmpty
    else initialContext.get().isEmpty
  }

  def put(key: String, value: String): Unit = {
    if (fiberAwareContext != null) fiberAwareContext.set(fiberAwareContext.get() + (key -> value))
    else initialContext.set(initialContext.get() + (key -> value))
  }

  def remove(key: String): Unit = {
    if (fiberAwareContext != null) fiberAwareContext.set(fiberAwareContext.get() - key)
    else fiberAwareContext.set(initialContext.get() - key)
  }
}

object FiberAwareThreadContextMap {
  private val initialContext: ThreadLocal[Map[String, String]] = ThreadLocal.withInitial(() => Map.empty)
  private var fiberAwareContext: FiberRef.UnsafeHandle[Map[String, String]] = _
  private val initialized = new AtomicBoolean(false)

  private[log4j2] def initFiberAwareContext(context: FiberRef.UnsafeHandle[Map[String, String]]): Unit = {
    if (context == null) {
      throw new NullPointerException("The context cannot be initialized with null")
    } else if (!initialized.compareAndSet(false, true)) {
      throw new IllegalStateException("The context must not be initialized more than once")
    } else {
      fiberAwareContext = context
    }
  }

  private[log4j2] def isInitialized = initialized.get()
}
