# ZIO Interop Log4J2
This library will provide fiber aware MDC logging for ZIO.

## Disclaimer
This library depends on ZIO APIs that are not yet available in `zio-1.0.0-RC17`. A first official release
to Maven Central is planned, as soon as `zio-1.0.0-RC18` is out.

## Usage
1. Add `-Dlog4j2.threadContextMap=com.github.mlangc.zio.interop.log4j2.FiberAwareThreadContextMap` to your JVM startup parameters. If you can't,
  try to set the `log4j2.threadContextMap` system property programmatically as early as you can. The property must be set before Log4j2 is
  initialized.
1. Integrate `com.github.mlangc.zio.interop.log4j2.FiberAwareThreadContextMap.assertInitialized` into your initialization code. Fiber aware MDC
   logging will not work properly without this call.
1. Use `org.slf4j.MDC` as you would normally and observe that it now respects fiber boundaries.
1. If you start getting annoyed by wrapping interactions with `MDC` in ZIO effects, take a look at [slf4zio](https://github.com/mlangc/slf4zio)
   which ships an `MDC` convenience wrapper for ZIO called `MDZIO`.
