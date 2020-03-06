# ZIO Interop Log4J2
[![Latest Release](https://img.shields.io/maven-central/v/com.github.mlangc/zio-interop-log4j2_2.13?color=green&label=latest-release)](https://oss.sonatype.org/content/repositories/releases/com/github/mlangc/zio-interop-log4j2_2.13)
[![Latest Snapshot](https://img.shields.io/nexus/s/com.github.mlangc/zio-interop-log4j2_2.13?label=latest-snapshot&server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/com/github/mlangc/zio-interop-log4j2_2.13/)

This library provides ZIO fiber aware MDC logging for Log4j 2.

## Usage
1. Add `-Dlog4j2.threadContextMap=com.github.mlangc.zio.interop.log4j2.FiberAwareThreadContextMap` to your JVM startup parameters. If you can't,
  try to set the `log4j2.threadContextMap` system property programmatically as early as you can. The property must be set before Log4j2 is
  initialized.
1. Integrate `com.github.mlangc.zio.interop.log4j2.FiberAwareThreadContextMap.assertInitialized` into your initialization code. Fiber aware MDC
   logging will not work properly without this call.
1. Use `org.slf4j.MDC` as you would normally and observe that it now respects fiber boundaries.
1. If you start getting annoyed by wrapping interactions with `MDC` in ZIO effects, take a look at [slf4zio](https://github.com/mlangc/slf4zio)
   which ships an `MDC` convenience wrapper for ZIO called `MDZIO`.
