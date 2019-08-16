package com.github.mlangc.zio.interop.log4j2

import zio.FiberRef
import zio.IO
import zio.Task

object MDCIO {
  def init: IO[InitializationError, Unit] =
    for {
      fiberRef <- FiberRef.make(Map.empty[String, String])
      handle <- fiberRef.getUnsafeHandle
      _ <- Task(FiberAwareThreadContextMap.initFiberAwareContext(handle)).mapError(new InitializationError(_))
    } yield ()
}
