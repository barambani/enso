package org.enso.interpreter.test.semantic

import cats.data.NonEmptyList
import cats.{Foldable, Monoid}
import cats.derived.semi
import org.enso.interpreter.test.InterpreterTest

class SimpleArithmeticTest extends InterpreterTest {
  "1" should "equal 1" in {
    eval("1") shouldEqual 1
  }

  "1 + 1" should "equal 2" in {
    eval("1 + 1") shouldEqual 2
  }

  "2 + (2 * 2)" should "equal 6" in {
    eval("2 + (2 * 2)") shouldEqual 6
  }

  "2 + 2 * 3" should "equal 8" in {
    eval("2 + 2 * 3") shouldEqual 8
  }

  "2 * 2 / 2" should "equal 2" in {
    eval("2 * 2 / 2") shouldEqual 2
  }

  "Things" should "work" in {
//    val code =
//      """
//        |(Cons h t) -> h
//        |""".stripMargin
//    val code =
//      """
//        |Cons h (Cons t Nil)
//        |""".stripMargin
//    val code = // type signature
//      """
//        |a : Cons h t -> a
//        |""".stripMargin
//    val code = // lambda
//      """
//        |(a : Cons h t) -> a
//        |""".stripMargin
//    val code = // lambda
//      """
//        |(Cons h t : a) -> a
//        |""".stripMargin

    case class Foo[A](a: NonEmptyList[A], b: NonEmptyList[A])
    implicit val fold: Foldable[Foo] = semi.foldable
    implicit val intMono: Monoid[Int] = new Monoid[Int] {
      override def empty: Int = 0

      override def combine(x: Int, y: Int): Int = x + y
    }

    println(fold.fold( Foo(NonEmptyList.of(1,2,3), NonEmptyList.of(10,20,30))))

    val code =
      """
        |if a then b else c
        |a + b * c
        |""".stripMargin
    eval(code)
  }
}
