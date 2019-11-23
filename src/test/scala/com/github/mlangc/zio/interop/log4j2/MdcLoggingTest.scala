package com.github.mlangc.zio.interop.log4j2

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.github.mlangc.slf4zio.api._
import org.slf4j.MDC
import zio.Managed
import zio.UIO
import zio.clock.Clock
import zio.clock.sleep
import zio.duration.Duration
import zio.internal.Executor
import zio.test.Assertion.equalTo
import zio.test.DefaultRunnableSpec
import zio.test._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

object MdcLoggingTest extends DefaultRunnableSpec with LoggingSupport {
  def spec = suite("MdcLoggingTest")(
    testM("Make sure we can log using fiber aware MDC data") {
      setup *> newSingleThreadExecutor.use { exec =>
        for {
          _ <- MDZIO.putAll("a" -> "1", "b" -> "2", "c" -> "3")
          _ <- logger.infoIO("Test")
          _ <- logger.infoIO("Test on other thread but same fiber").lock(exec)
          fiber1 <- {
            MDZIO.put("c", "3*") *> logger.infoIO("Test on child fiber1")
            }.fork
          fiber2 <- {
            MDZIO.put("b", "2*") *> logger.infoIO("Test on child fiber2")
            }.fork
          _ <- sleep(Duration.apply(10, TimeUnit.MILLISECONDS)).provide(Clock.Live)
          _ <- logger.infoIO("Test on parent fiber")
          _ <- fiber1.join
          _ <- logger.infoIO("Test on parent fiber after first join")
          _ <- fiber2.join
          _ <- logger.infoIO("Test on parent fiber after second join")
          events <- UIO(TestLog4j2Appender.events)
        } yield {
          assert(events.size, equalTo(7)) &&
            assert(events.last.getContextData.toMap.asScala, equalTo(Map("a" -> "1", "b" -> "2", "c" -> "3"))) &&
            assert(events.head.getContextData.toMap.asScala, equalTo(Map("a" -> "1", "b" -> "2*", "c" -> "3")))
        }
      }
    }
  )

  private def newSingleThreadExecutor: Managed[Nothing, Executor] =
    UIO(Executors.newSingleThreadExecutor())
      .toManaged(exec => UIO(exec.shutdown()))
      .map(exec => Executor.fromExecutionContext(Int.MaxValue)(ExecutionContext.fromExecutorService(exec)))

  private def setup: UIO[Unit] =
    FiberAwareThreadContextMap.assertInitialized *> UIO {
      MDC.clear()
      TestLog4j2Appender.reset()
    }
}
