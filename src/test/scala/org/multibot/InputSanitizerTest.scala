package org.multibot

import org.scalatest.{Assertion, FlatSpec}

class InputSanitizerTest extends FlatSpec {

  def ensureSanitizedInput(in: String, expected: String): Assertion =
    assert(Sanitizer.sanitizeInput(in) === expected)

  def ensureNoSanitization(in: String) : Assertion =
    ensureSanitizedInput(in, in)

  "Inputs" should "be sanitized" in {
    ensureSanitizedInput("```foo```", "foo")
    ensureSanitizedInput("```\nfoo\n```", "foo")
    ensureSanitizedInput("``` \nfoo\n ```", "foo")
    ensureSanitizedInput("``` \nfoo \n```", "foo")
    ensureSanitizedInput("`foo`", "foo")
    ensureSanitizedInput("```scala\nfoo```", "foo")
    ensureSanitizedInput("```scala \nfoo```", "foo")
    ensureSanitizedInput("```scala \nfoo\n```", "foo")

    // I have no idea why anyone would do this, but make sure the result is expected
    ensureSanitizedInput("````foo````", "`foo`")
    ensureSanitizedInput("``foo``", "`foo`")
  }

  it should "not be sanitized" in {
    ensureNoSanitization("foo")
  }
}
