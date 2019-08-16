package com.github.mlangc.zio.interop.log4j2

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext
import com.github.mlangc.slf4zio.api._
import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.slf4j.MDC
import zio.DefaultRuntime
import zio.Managed
import zio.Task
import zio.UIO
import zio.clock.sleep
import zio.duration.Duration
import zio.internal.Executor

import scala.collection.JavaConverters._

class FiberAwareThreadContextMapTest extends FreeSpec with LoggingSupport with DefaultRuntime with BeforeAndAfter {
  before {
    MDC.clear()
    System.setProperty("log4j2.threadContextMap", classOf[FiberAwareThreadContextMap].getCanonicalName)
    TestLog4j2Appender.reset()
  }

  "Make sure we can log something" in {
    MDC.put("a", "1")
    MDC.put("b", "2")
    MDC.put("c", "3")
    logger.info("Hello world")
  }

  "Make sure we can log with ZIO" in {
    unsafeRun {
      newSingleThreadExecutor.use { exec =>
        for {
          _ <- MDCIO.init
          _ <- Task {
            MDC.put("a", "1")
            MDC.put("b", "2")
            MDC.put("c", "3")
          }

          _ <- logger.infoIO("Test")
          _ <- logger.infoIO("Test on other thread but same fiber").lock(exec)
          fiber1 <- {
            UIO(MDC.put("c", "3*")) *> logger.infoIO("Test on child fiber1")
          }.fork
          fiber2 <- {
            UIO(MDC.put("b", "2*")) *> logger.infoIO("Test on child fiber2")
          }.fork
          _ <- sleep(Duration.apply(10, TimeUnit.MILLISECONDS))
          _ <- logger.infoIO("Test on parent fiber")
          _ <- fiber1.join
          _ <- logger.infoIO("Test on parent fiber after first join")
          _ <- fiber2.join
          _ <- logger.infoIO("Test on parent fiber after second join")
          events <- UIO(TestLog4j2Appender.events)
          _ <- Task {
            assert(events.size === 7)
            assert(events.last.getContextData.toMap.asScala === Map("a" -> "1", "b" -> "2", "c" -> "3"))
            assert(events.head.getContextData.toMap.asScala === Map("a" -> "1", "b" -> "2*", "c" -> "3"))
          }
        } yield ()
      }
    }
  }

  private def newSingleThreadExecutor: Managed[Nothing, Executor] =
    UIO(Executors.newSingleThreadExecutor())
      .toManaged(exec => UIO(exec.shutdown()))
      .map(exec => Executor.fromExecutionContext(Int.MaxValue)(ExecutionContext.fromExecutorService(exec)))
}
