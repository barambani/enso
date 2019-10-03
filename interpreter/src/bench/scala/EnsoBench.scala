import org.enso.interpreter.Constants
import org.enso.interpreter.LanguageRunner
import org.graalvm.polyglot.Context
import org.scalameter.api._

class EnsoBench extends Bench.LocalTime with LanguageRunner {
  val gen = Gen.unit("")

  val countTCOCode =
    """
      |{ |upto|
      |  counter = { |current|
      |      ifZero: [current, 0, @counter [current - 1]]
      |  };
      |  res = @counter [upto];
      |  res
      |}
    """.stripMargin

  val countTCO = ctx.eval(Constants.LANGUAGE_ID, countTCOCode)

  performance of "Enso TCO" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        countTCO.call(1000000)
      }
    }
  }

  val countTCOCorecursiveCode =
    """
      |{ |upto|
      |  counter = { |current|
      |      ifZero: [current, 0, #{ @counter [current - 1] }]
      |  };
      |  res = @counter [upto];
      |  res
      |}
    """.stripMargin

  val countTCOCorecursive = ctx.eval(Constants.LANGUAGE_ID, countTCOCorecursiveCode)

  performance of "Enso TCO Corecursion" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        countTCOCorecursive.call(1000000)
      }
    }
  }

  val countTCOCustomIfCode =
    """
      |{ |upto|
      |  if = { |cond, ifT, ifF| ifZero: [cond, #ifT, #ifF] };
      |  counter = { |current| @if [current, {0}, { #counter [current - 1] } ] };
      |  res = @counter [upto];
      |  res
      |}
      |""".stripMargin

  val countTCOCustomIf = eval(countTCOCustomIfCode)

  performance of "Enso TCO custom if" in {
    measure method "sum numbers upto a million" in {
      using(gen) in { _ =>
        countTCOCustomIf.call(1000000)
      }
    }
  }
}
