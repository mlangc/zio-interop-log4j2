package com.github.mlangc.zio.interop.log4j2


import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginFactory

@Plugin(name = "Test", category = "Core", elementType = "appender", printObject = true)
class TestLog4j2Appender(name: String) extends AbstractAppender(name, null, null, false, Array()) {
  def append(event: LogEvent): Unit = TestLog4j2Appender.appendEvent(event.toImmutable)
}

object TestLog4j2Appender {
  private val logEventsRef: AtomicReference[List[LogEvent]] = new AtomicReference[List[LogEvent]](Nil)

  def reset(): Unit = logEventsRef.set(Nil)

  @PluginFactory()
  def createAppender(@PluginAttribute("name") name: String): TestLog4j2Appender = {
    new TestLog4j2Appender(name)
  }

  def events: List[LogEvent] = logEventsRef.get()

  @tailrec
  private def appendEvent(event: LogEvent): Unit = {
    val old = logEventsRef.get()
    if (logEventsRef.compareAndSet(old, event :: old)) ()
    else appendEvent(event)
  }

}
