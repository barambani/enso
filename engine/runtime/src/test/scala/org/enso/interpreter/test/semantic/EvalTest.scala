package org.enso.interpreter.test.semantic

import org.enso.interpreter.test.InterpreterTest

class EvalTest extends InterpreterTest {
  "Debug.eval" should "evaluate a string expression" in {
    val code =
      """
        |@{
        |  @eval [@Debug, "@println[@IO, \"foo\"]"]
        |}
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("foo")
  }

  "Debug.eval" should "have access to the caller scope" in {
    val code =
      """
        |@{
        |  x = "Hello World!";
        |  @eval [@Debug, "@println[@IO, x]"]
        |}
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("Hello World!")
  }

  "Debug.eval" should "have access to the caller module scope" in {
    val code =
      """
        |type MyType x;
        |
        |@{
        |  x = 10;
        |  @eval [@Debug, "@println[@IO, @MyType[x]]"]
        |}
        |""".stripMargin
    eval(code)
    consumeOut shouldEqual List("MyType<10>")
  }

  "Debug.eval" should "return a value usable in the caller scope" in {
    val code =
      """
        |@{
        |  x = 1;
        |  y = 2;
        |  res = @eval [@Debug, "x + y"];
        |  res + 1
        |}
        |""".stripMargin
    eval(code) shouldEqual 4
  }
}
