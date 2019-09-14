package com.github.mlangc.zio.interop.log4j2

import java.util
import java.util.concurrent.atomic.AtomicReference

import com.github.mlangc.zio.interop.log4j2.MDZIO.EmptyReadOnlyStringMap
import com.github.mlangc.zio.interop.log4j2.MDZIO.ReadOnlyStringMapAdapter
import com.github.mlangc.zio.interop.log4j2.MDZIO.populate
import com.github.mlangc.zio.interop.log4j2.MDZIO.refWithHandle
import org.apache.logging.log4j.core.ContextDataInjector
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.impl.ThreadContextDataInjector
import org.apache.logging.log4j.util.BiConsumer
import org.apache.logging.log4j.util.ReadOnlyStringMap
import org.apache.logging.log4j.util.StringMap
import org.apache.logging.log4j.util.TriConsumer
import zio.FiberRef
import zio.FiberRef.UnsafeHandle
import zio.UIO
import zio.ZIO

import scala.collection.JavaConverters._
import scala.collection.mutable

class MDZIO extends ContextDataInjector {
  def injectContextData(properties: util.List[Property], reusable: StringMap): StringMap =
    if (properties == null || properties.isEmpty) {
      populate(reusable)
    } else {
      ThreadContextDataInjector.copyProperties(properties, reusable)
      populate(reusable)
    }

  def rawContextData(): ReadOnlyStringMap =
    refWithHandle.map { refWithHandle =>
      new ReadOnlyStringMapAdapter(refWithHandle.handle.get)
    }.getOrElse(EmptyReadOnlyStringMap)
}

object MDZIO {
  private case class RefWithHandle(ref: FiberRef[Map[String, String]], handle: UnsafeHandle[Map[String, String]])
  private val refWithHandleRef: AtomicReference[RefWithHandle] = new AtomicReference[RefWithHandle](null)
  private def refWithHandle: Option[RefWithHandle] = Option(refWithHandleRef.get())

  def put(key: String, value: String): UIO[Unit] =
    getRef >>= { ref =>
      ref.update(_ + (key -> value)).unit
    }

  def putAll(prop: (String, String), props: (String, String)*): UIO[Unit] =
    getRef >>= { ref =>
      ref.update(map => (map + prop) ++ props).unit
    }

  def remove(key: String): UIO[Unit] =
    getRef >>= { ref =>
      ref.update(_ - key).unit
    }

  def clear: UIO[Unit] =
    getRef >>= { ref =>
      ref.set(Map.empty)
    }

  private def getRefWithHandle: UIO[RefWithHandle] =
    ZIO.fromOption(refWithHandle).catchAll { _ =>
      for {
        newRef <- FiberRef.make(Map.empty[String, String])
        newHandle <- newRef.getUnsafeHandle
        newRefWithHandle = RefWithHandle(newRef, newHandle)
        updated <- UIO(refWithHandleRef.compareAndSet(null, newRefWithHandle))
        res <- if (updated) ZIO.succeed(newRefWithHandle) else getRefWithHandle
      } yield res
    }

  private def getRef: UIO[FiberRef[Map[String, String]]] =
    getRefWithHandle.map(_.ref)

  private def populate(reusable: StringMap): StringMap =
    refWithHandle match {
      case None => reusable

      case Some(RefWithHandle(_, handle)) =>
        handle.get.foreach(entry => reusable.putValue(entry._1, entry._2))
        reusable
    }

  private class ReadOnlyStringMapAdapter(underlying: Map[String, String]) extends ReadOnlyStringMap {
    def toMap: util.Map[String, String] = (mutable.Map.empty[String, String] ++ underlying).asJava
    def containsKey(key: String): Boolean = underlying.contains(key)

    def forEach[V](action: BiConsumer[String, _ >: V]): Unit =
      underlying.foreach { case (key, value) =>
        action.accept(key, value.asInstanceOf[V])
      }

    def forEach[V, S](action: TriConsumer[String, _ >: V, S], state: S): Unit =
      underlying.foreach { case (key, value) =>
          action.accept(key, value.asInstanceOf[V], state)
      }

    def getValue[V](key: String): V = underlying.get(key).orNull.asInstanceOf[V]
    def isEmpty: Boolean = underlying.isEmpty
    def size(): Int = underlying.size
  }

  private object EmptyReadOnlyStringMap extends ReadOnlyStringMap {
    def toMap: util.Map[String, String] = new util.HashMap[String, String](2)
    def containsKey(key: String): Boolean = false
    def forEach[V](action: BiConsumer[String, _ >: V]): Unit = ()
    def forEach[V, S](action: TriConsumer[String, _ >: V, S], state: S): Unit = ()
    def getValue[V](key: String): V = null.asInstanceOf[V]
    def isEmpty: Boolean = true
    def size(): Int = 0
  }
}
