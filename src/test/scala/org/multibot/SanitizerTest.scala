package org.multibot

import org.scalatest.{Assertion, FlatSpec}

class SanitizerTest extends FlatSpec {

  def ensureSanitizedInput(in: String, expected: String): Assertion =
    assert(InputSanitizer.sanitize(in) === expected)

  def ensureSanitizedOutput(out: String, expected: String): Assertion =
    assert(OutputSanitizer(out) === expected)

  def ensureNoSanitizationInput(in: String) : Assertion =
    ensureSanitizedInput(in, in)

  def ensureNoSanitizationOutput(out: String) : Assertion =
    ensureSanitizedOutput(out, out)

  "Inputs" should "be sanitized" in {
    ensureSanitizedInput("```foo```", "foo")
    ensureSanitizedInput("`foo`", "foo")

    // I have no idea why anyone would do this, but make sure the result is expected
    ensureSanitizedInput("````foo````", "`foo`")
    ensureSanitizedInput("``foo``", "`foo`")
  }

  it should "not be sanitized" in {
    ensureNoSanitizationInput("foo")
  }

  "Outputs" should "be sanitized" in {
    ensureSanitizedOutput("foo\rbar", "foobar")
    ensureSanitizedOutput("\rfoobar", "foobar")
    ensureSanitizedOutput("foobar\r", "foobar")
    ensureSanitizedOutput("```foobar```", "'''foobar'''")
    ensureSanitizedOutput("`foobar`", "'foobar'")
    ensureSanitizedOutput(
      "`I```Am``Trying``To``Ruin`````This``Bots```Day",
      "'I'''Am''Trying''To''Ruin'''''This''Bots'''Day")
  }
}
