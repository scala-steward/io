/*
 * sbt IO
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.io

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object RetrySpec extends verify.BasicTestSuite {
  private val noExcluded: List[Class[? <: IOException]] = List[Class[? <: IOException]]()
  test("retry should throw first exception after number of failures") {
    val i = new AtomicInteger()
    def throww(): Any = throw new IOException(i.incrementAndGet().toString)
    try {
      Retry(throww(), limit = 10, sleepInMillis = 0, noExcluded: _*)
      assert(false)
    } catch {
      case ioe: IOException =>
        assert(ioe.getMessage == "1")
        assert(i.get() == 10)
    }
  }

  test("retry should throw recover") {
    for (recoveryStep <- (1 to 14)) {
      val i = new AtomicInteger()
      val value = Retry(
        {
          val thisI = i.incrementAndGet()
          if (thisI == recoveryStep) "recover" else throw new IOException(thisI.toString)
        },
        limit = 15,
        sleepInMillis = 0,
        noExcluded: _*
      )
      assert(value == "recover")
    }
  }

  test("retry should recover from non-IO exceptions") {
    val i = new AtomicInteger()
    def throww(): Any =
      if (i.incrementAndGet() == 5) 0
      else ???
    Retry(throww())
    ()
  }
}
