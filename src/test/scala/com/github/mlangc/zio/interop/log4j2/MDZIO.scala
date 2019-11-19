package com.github.mlangc.zio.interop.log4j2

import org.slf4j.MDC
import zio.UIO
import zio.ZIO

object MDZIO {
  def put(key: String, value: String): UIO[Unit] =
    UIO(MDC.put(key, value))

  def get(key: String): UIO[Option[String]] =
    UIO(Option(MDC.get(key)))

  def remove(key: String): UIO[Unit] =
    UIO(MDC.remove(key))

  def clear(): UIO[Unit] =
    UIO(MDC.clear())

  def putAll(pairs: (String, String)*): UIO[Unit] =
    putAll(pairs)

  def putAll(pairs: Iterable[(String, String)]): UIO[Unit] =
    ZIO.foreach_(pairs)((put _).tupled)
}
