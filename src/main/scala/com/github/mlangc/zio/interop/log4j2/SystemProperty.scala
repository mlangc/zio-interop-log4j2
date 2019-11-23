package com.github.mlangc.zio.interop.log4j2

private[log4j2] case class SystemProperty(key: String, value: String)

private[log4j2] object SystemProperty {
  val ThreadContextMap =
    SystemProperty("log4j2.threadContextMap", classOf[FiberAwareThreadContextMap].getCanonicalName)
}
