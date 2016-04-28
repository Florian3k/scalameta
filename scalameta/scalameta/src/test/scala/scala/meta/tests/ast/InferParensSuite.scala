package scala.meta.tests
package tokens

import org.scalatest._
import scala.meta._
import scala.meta.dialects.Scala211
import scala.meta.internal.prettyprinters.inferTokens

class InferParensSuite extends FunSuite {
  private def test(name: String)(code: String) {
    def compareTokenCodes(a: Tree, b: Tree): Unit = {
      def trimTokens(tks: Tokens) = tks.filterNot(tk => tk.is[Token.BOF] || tk.is[Token.EOF])
      val (t1, t2) = (trimTokens(a.tokens).map(_.show[Syntax]), trimTokens(b.tokens).map(_.show[Syntax]))
      if (t1 != t2) {
        println(a.show[Syntax] + "\n" + b.show[Syntax])
      }
      assert(t1 == t2)
    }

    super.test(name) {
      val tree = code.stripMargin.parse[Term].get
      compareTokenCodes(tree, tree.resetAllTokens)
    }
  }

  test("SameOpSucc1") {
    """x1 :: x2 :: xs"""
  }
  test("SameOpSucc2") {
    """x1 :: x2 :: x3 :: xs"""
  }
  test("CheckMixedAssoc1") {
    """(x :+ y) :: xs"""
  }
  test("CheckMixedAssoc2") {
    """x :+ (y :: xs)"""
  }
  test("CheckMixedAssoc3") {
    """x :: y +: z"""
  }
  test("CheckMixedAssoc4") {
    """(x :: y) +: z"""
  }
  test("CheckMixedAssoc5") {
    """x :+ y :+ z"""
  }
  test("CheckMixedAssoc6") {
    """x :+ (y :+ z)"""
  }
  test("CheckMixedAssoc7") {
    """x eq (y :: xs)"""
  }
  test("CheckMixedAssoc8") {
    """(x eq y) :: xs"""
  }
  test("CheckMixedAssoc9") {
    """a == b || c == d || y && d"""
  }
  test("CheckMixedAssoc10") {
    """a && (b || c)"""
  }
  test("CheckMixedAssoc11") {
    """a && b || c"""
  }
  test("CheckMixedAssoc12") {
    """x :+ y :+ z"""
  }
  test("CheckMixedAssoc14") {
    """(x + y) / z"""
  }
}