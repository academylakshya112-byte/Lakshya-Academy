package com.example

import org.junit.Test

class ExampleUnitTest {
  @Test
  fun printEnv() {
    println("=== ENV VARIABLES ===")
    System.getenv().forEach { (k, v) ->
        val masked = if (v.length > 6) v.take(6) + "..." else "..."
        println("$k = $masked (length: ${v.length})")
    }
    println("=====================")
  }
}
