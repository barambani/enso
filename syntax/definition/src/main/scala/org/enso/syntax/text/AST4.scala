package org.enso.syntax.text.test4

import monocle.macros.GenLens
import org.enso.data.List1._
import org.enso.data.List1
import org.enso.data.Shifted
import org.enso.data.Tree
import org.enso.syntax.text.ast.{Repr => RRR}
import org.enso.syntax.text.ast.Repr.R
import org.enso.syntax.text.ast.Repr
import org.enso.syntax.text.ast.opr
import org.enso.syntax.text.ast.text
import java.util.UUID

import scala.annotation.tailrec

import cats.Functor
import cats.implicits._
import cats.derived._

object AST {

  type Repr[T] = Repr.Of[T]

  def map[F[_]: Functor, A, B](t: F[A])(f: A => B): F[B] = Functor[F].map(t)(f)

  //////////////////////////////////////////////////////////////////////////////
  //// Reexports ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Assoc = opr.Assoc

  val Assoc = opr.Assoc
  val Prec  = opr.Prec

  //////////////////////////////////////////////////////////////////////////////
  //// Definition //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Structure ////

  type AST            = ASTOf[FAST]
  type FAST           = Fix[TaggedASTOf]
  type TaggedAST      = TaggedASTOf[FAST]
  type TaggedASTOf[T] = Tagged[ASTOf[T]]

  //// Aliases ////

  type SAST        = Shifted[AST]
  type _Stream[T]  = List[Shifted[T]]
  type _Stream1[T] = List1[Shifted[T]]
  type Stream      = _Stream[AST]
  type Stream1     = _Stream1[AST]
  type ID          = UUID

  //// API ////

  implicit class ASTOps[T[_]](ast: T[FAST]) {
    import AST.Scheme.implicits._

    def repr:     RRR = ast.repr
    def span:     Int = repr.span
    def byteSpan: Int = repr.byteSpan

    def map(f: TaggedAST => TaggedAST)(
      implicit
      ftorT: Functor[T],
      reprT: Repr[T[FAST]]
    ): T[FAST] = Functor[T].map(ast)(_.map(f))

    def mapWithOff(f: (Int, TaggedAST) => TaggedAST)(
      implicit
      ftorT: Functor[T],
      offZipT: OffsetZip[T, FAST],
      reprT: Repr[T[FAST]]
    ): T[FAST] = {
      val zipped = OffsetZip(ast)
      Functor[T].map(zipped) { case (i, fx) => fx.map(f(i, _)) }
    }

    def traverseWithOff(f: (Int, TaggedAST) => TaggedAST)(
      implicit
      ftorT: Functor[T],
      offZipT: OffsetZip[T, FAST],
      reprT: Repr[T[FAST]]
    ): T[FAST] = {
      def go(i: Int, t: TaggedAST): TaggedAST = {
        val newStruct = t.struct.mapWithOff { (j, ast) =>
          val off = i + j
          go(off, f(off, ast))
        }
        t.copy(struct = newStruct)
      }
      mapWithOff { (off, ast) =>
        go(off, f(off, ast))
      }
    }
  }

  def tokenize(ast: AST): Shifted.List1[AST] = {
    @tailrec
    def go(ast: AST, out: AST.Stream): Shifted.List1[AST] =
      ast match {
        case t: App.Prefix => go(t.fn, Shifted(t.off, t.arg.struct) :: out)
        case anyAst        => Shifted.List1(anyAst, out)
      }
    go(ast, List())
  }

  object implicits extends Ident.implicits with Literal.implicits {
    implicit def stringToAST(str: String): AST = {
      if (str == "") throw new Error("Empty literal")
      if (str == "_") Blank()
      else if (str.head.isLower) Var(str)
      else if (str.head.isUpper) Cons(str)
      else Opr(str)
    }
  }

  //// Instances ////

  implicit def offZipStream[T: Repr]: OffsetZip[_Stream, T] =
    stream => {
      var off = 0
      stream.map { t =>
        off += t.off
        val out = t.map((off, _))
        off += Repr.span(t.el)
        out
      }
    }

  //////////////////////////////////////////////////////////////////////////////
  //// OffsetZip ///////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  trait OffsetZip[F[A], A] {
    def zipWithOffset(t: F[A]): F[(Int, A)]
  }
  object OffsetZip {
    def apply[F[A], A](implicit ev: OffsetZip[F, A]): OffsetZip[F, A] = ev
    def apply[F[A], A](t: F[A])(implicit ev: OffsetZip[F, A]): F[(Int, A)] =
      OffsetZip[F, A].zipWithOffset(t)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Catamorphism ////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  case class Fix[F[_]](unFix: Fix.Body[F]) {
    def map(f: Fix.Body[F] => Fix.Body[F]): Fix[F] = copy(unFix = f(unFix))
  }
  object Fix {
    type Body[F[_]] = F[Fix[F]]
    implicit def reprFix[F[_]](
      implicit ev: Repr[Fix.Body[F]]
    ): Repr[Fix[F]] = t => Repr.of(t.unFix)
  }

  implicit def fix(t: Fix.Body[TaggedASTOf]): Fix[TaggedASTOf] = Fix(t)
  implicit def fixDeep[T[_] <: ASTOf[_]](t: T[FAST])(
    implicit
    ev: Repr[T[FAST]]
  ): Fix[TaggedASTOf] = {
    val t1 = Tagged(t)
    val t2 = t1.asInstanceOf[Fix.Body[TaggedASTOf]] // FIXME
    Fix(t2)
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Tagged //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** [[Tagged]] is a AST nodes wrapper which adds [[ID]] information and
    * caches the repr of the node.
    *
    * @param struct Is the structure of the AST node. In most cases, it is a
    *               subtype of [[AST]].
    * @param id     Is the unique AST Node ID assigned by parser from the marker
    *               map.
    */
  case class Tagged[+F: Repr](struct: F, id: Option[ID] = None) {
    val repr: RRR = Repr.of(struct)
  }

  implicit class TaggedOps[T: Repr](t: Tagged[T]) {
    def withNewID() = t.copy(id = Some(UUID.randomUUID()))
  }

  //// Conversions ////

  implicit def toTagged[T: Repr](t: T):     Tagged[T]       = Tagged(t, None)
  implicit def fromTagged[T](t: Tagged[T]): T               = t.struct
  implicit def reprWithData:                Repr[Tagged[_]] = _.repr
  implicit def taggedToASTOps[T[_]](v: Tagged[T[FAST]]): ASTOps[T] =
    v.struct

  //////////////////////////////////////////////////////////////////////////////
  //// Scheme //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** [[AST]] defines the shape of an AST node. It contains information about
    * the layout of elements and spacing between them.
    *
    * @tparam T The type of elements.
    */
  sealed trait ASTOf[T]

//  type Shape = _Shape[FixedAST]
  implicit def ftorScheme: Functor[ASTOf] = semi.functor

  object Scheme {
    object implicits extends implicits
    trait implicits {
      import Ident.implicits._
//      import App.implicits._

      // TODO: Should be auto-generated with Shapeless
      implicit def reprScheme: Repr[ASTOf[FAST]] = {
        case t: Blank => Ident.implicits.reprBlank.of(t)
        case t: Var   => Ident.implicits.reprVar.of(t)
        case t: Cons  => Ident.implicits.reprCons.of(t)
//        case t: App._Prefix[_AST] =>
//          App.implicits.reprPrefix[_AST].of(t)
      }

      // TODO: Should be auto-generated with Shapeless
      implicit def offZipScheme[T: Repr]: OffsetZip[ASTOf, T] = {
        case t: Ident.VarOf[T]  => OffsetZip(t)
        case t: Ident.ConsOf[T] => OffsetZip(t)
//        case t: App._Prefix[T] => OffsetZip(t)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Phantom /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  /** Phantom type. Use with care, as Scala cannot prove its proper usage. When
    * a type is phantom, then its last type argument is not used and we can
    * safely coerce it to something else.
    */
  trait Phantom
  implicit class PhantomOps[T[_] <: Phantom](ident: T[_]) {
    def coerce[S]: T[S] = ident.asInstanceOf[T[S]]
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Invalid /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Invalid      = InvalidOf[FAST]
  type Unrecognized = _Unrecognized[FAST]
  type Unexpected   = _Unexpected[FAST]

  sealed trait InvalidOf[T]                extends ASTOf[T]
  case class _Unrecognized[T](str: String) extends InvalidOf[T] with Phantom
  case class _Unexpected[T](msg: String, stream: _Stream[T])
      extends InvalidOf[T]

  implicit def ftorInvalid:      Functor[InvalidOf]     = semi.functor
  implicit def ftorUnexpected:   Functor[_Unexpected]   = semi.functor
  implicit def ftorUnrecognized: Functor[_Unrecognized] = semi.functor
  implicit def reprUnrecognized: Repr[_Unrecognized[_]] = _.str
  implicit def reprUnexpected[T: Repr]: Repr[_Unexpected[T]] =
    t => Repr.of(t.stream)
  implicit def offZipUnrecognized[T]: OffsetZip[_Unrecognized, T] =
    t => t.coerce
  implicit def offZipUnexpected[T: Repr]: OffsetZip[_Unexpected, T] =
    t => t.copy(stream = OffsetZip(t.stream))

  //////////////////////////////////////////////////////////////////////////////
  //// Ident ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Ident = IdentOf[FAST]
  sealed trait IdentOf[T] extends ASTOf[T] with Phantom { val name: String }

  implicit class IdentOps(t: Tagged[Ident]) {
    def name: String = t.struct.name
  }

  type Blank = Ident.Blank
  type Var   = Ident.Var
  type Cons  = Ident.Cons
  type Opr   = Ident.Opr
  type Mod   = Ident.Mod

  val Blank = Ident.Blank
  val Var   = Ident.Var
  val Cons  = Ident.Cons
  val Opr   = Ident.Opr
  val Mod   = Ident.Mod

  object Ident {
    case class InvalidSuffix[T](elem: Tagged[Ident], suffix: String)
        extends InvalidOf[T]

    //// Definition ////

    type Blank = BlankOf[FAST]
    type Var   = VarOf[FAST]
    type Cons  = ConsOf[FAST]
    type Opr   = OprOf[FAST]
    type Mod   = ModOf[FAST]

    case class BlankOf[T]()            extends IdentOf[T] { val name = "_" }
    case class VarOf[T](name: String)  extends IdentOf[T]
    case class ConsOf[T](name: String) extends IdentOf[T]
    case class ModOf[T](name: String)  extends IdentOf[T]
    case class OprOf[T](name: String) extends IdentOf[T] {
      val (prec, assoc) = opr.Info.of(name)
    }

    //// Companions ////

    object Blank {
      def apply():           Blank   = BlankOf()
      def unapply(t: Blank): Boolean = true
    }

    object Var {
      def apply(name: String): Var            = VarOf(name)
      def unapply(t: Var):     Option[String] = Some(t.name)
      trait implicits {
        implicit def stringToVar(str: String): Var = Var(str)
      }
    }

    object Cons {
      def apply(name: String): Cons           = ConsOf(name)
      def unapply(t: Cons):    Option[String] = Some(t.name)
      trait implicits {
        implicit def stringToCons(str: String): Cons = Cons(str)
      }
    }

    object Opr {
      def apply(name: String): Opr            = OprOf(name)
      def unapply(t: Opr):     Option[String] = Some(t.name)
      trait implicits {
        implicit def stringToOpr(str: String): Opr = Opr(str)
      }
      val app: Opr = Opr(" ")
    }

    object Mod {
      def apply(name: String): Mod            = ModOf(name)
      def unapply(t: Mod):     Option[String] = Some(t.name)
      trait implicits {
        implicit def stringToMod(str: String): Mod = Mod(str)
      }
    }

    //// Instances ////

    object implicits extends implicits
    trait implicits
        extends Scheme.implicits
        with Var.implicits
        with Cons.implicits
        with Opr.implicits
        with Mod.implicits {
      implicit def reprBlank:      Repr[BlankOf[_]]      = _.name
      implicit def reprVar:        Repr[VarOf[_]]        = _.name
      implicit def reprCons:       Repr[ConsOf[_]]       = _.name
      implicit def reprOpr:        Repr[OprOf[_]]        = _.name
      implicit def reprMod:        Repr[ModOf[_]]        = _.name
      implicit def ftorIdent:      Functor[IdentOf]      = semi.functor
      implicit def ftorBlank:      Functor[BlankOf]      = semi.functor
      implicit def ftorVar:        Functor[VarOf]        = semi.functor
      implicit def ftorOpr:        Functor[OprOf]        = semi.functor
      implicit def ftorMod:        Functor[ModOf]        = semi.functor
      implicit def offZipBlank[T]: OffsetZip[BlankOf, T] = t => t.coerce
      implicit def offZipVar[T]:   OffsetZip[VarOf, T]   = t => t.coerce
      implicit def offZipCons[T]:  OffsetZip[ConsOf, T]  = t => t.coerce
      implicit def offZipOpr[T]:   OffsetZip[OprOf, T]   = t => t.coerce
      implicit def offZipMod[T]:   OffsetZip[ModOf, T]   = t => t.coerce

      implicit def stringToIdent(str: String): Ident = {
        if (str == "") throw new Error("Empty literal")
        if (str == "_") Blank()
        else if (str.head.isLower) Var(str)
        else if (str.head.isUpper) Cons(str)
        else Opr(str)
      }
    }
  }
  import Ident.implicits._

  //////////////////////////////////////////////////////////////////////////////
  //// Literal /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Literal = _Literal[FAST]
  sealed trait _Literal[T] extends ASTOf[T]

//  implicit def ftorLiteral:    Functor[_Literal]    = semi.functor

  object Literal {

    object implicits extends implicits
    trait implicits  extends Number.implicits

    ////////////////
    //// Number ////
    ////////////////

    type Number = _Number[FAST]
    case class _Number[T](base: Option[String], int: String)
        extends _Literal[T]
        with Phantom

    object Number {
      def apply(i: String):            Number = _Number(None, i)
      def apply(b: String, i: String): Number = _Number(Some(b), i)
      def apply(i: Int):               Number = Number(i.toString)
      def apply(b: Int, i: String):    Number = Number(b.toString, i)
      def apply(b: String, i: Int):    Number = Number(b, i.toString)
      def apply(b: Int, i: Int):       Number = Number(b.toString, i.toString)

      object implicits extends implicits
      trait implicits {
        implicit def reprNumber: Repr[_Number[_]] =
          t => t.base.map(_ + "_").getOrElse("") + t.int
        implicit def ftorNumber: Functor[_Number] = semi.functor
        implicit def offZipNumber[T]: OffsetZip[_Number, T] =
          t => t.coerce
      }

      //// DanglingBase ////

//      case class DanglingBase(base: String) extends AST.Invalid {
//        val repr               = base + '_'
//        def map(f: AST => AST) = this
//      }
    }

    //////////////
    //// Text ////
    //////////////

    type Text = _Text[FAST]
    sealed trait _Text[T] extends ASTOf[T]
    object Text {

      //// Definition ////

      type Line[T]  = List[T]
      type Block[T] = List1[Line[T]]

      type Raw          = _Raw[FAST]
      type Interpolated = _Interpolated[FAST]
      type Unclosed     = _Unclosed[FAST]

      case class _Raw[T](quote: Quote, lines: Raw.Block[T]) extends _Text[T] {
        val quoteChar: Char = '"'
      }
      case class _Interpolated[T](quote: Quote, lines: Interpolated.Block[T])
          extends _Text[T] {
        val quoteChar: Char = '\''
      }
      case class _Unclosed[T](text: _Text[T]) extends AST.InvalidOf[T]

      object Raw {
        type Segment[T] = Text.Segment._Raw[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      object Interpolated {
        type Segment[T] = Text.Segment._Interpolated[T]
        type Line[T]    = Text.Line[Segment[T]]
        type Block[T]   = Text.Block[Segment[T]]
      }

      // Instances ////

      object implicits extends implicits
      trait implicits extends Segment.implicits {
        implicit def reprTextRaw[T]:      Repr[_Raw[T]]          = _ => ???
        implicit def reprTextInt[T]:      Repr[_Interpolated[T]] = _ => ???
        implicit def reprTextUnclosed[T]: Repr[_Unclosed[T]]     = _ => ???

        implicit def ftorTextRaw[T]: Functor[_Raw] =
          semi.functor
        implicit def ftorTextInterpolated[T]: Functor[_Interpolated] =
          semi.functor
        implicit def ftorTextUnclosed[T]: Functor[_Unclosed] =
          semi.functor
        implicit def offZipTextRaw[T]: OffsetZip[_Raw, T] =
          t => t.copy(lines = t.lines.map(_.map(OffsetZip(_))))
        implicit def offZipTextInt[T]: OffsetZip[_Interpolated, T] =
          t => t.copy(lines = t.lines.map(_.map(OffsetZip(_))))
        implicit def offZipUnclosed[T]: OffsetZip[_Unclosed, T] =
          t => t.copy(text = OffsetZip(t.text))
        implicit def offZipText[T]: OffsetZip[_Text, T] = {
          case t: _Raw[T]          => OffsetZip(t)
          case t: _Interpolated[T] => OffsetZip(t)
        }
      }

      ///////////////
      //// Quote ////
      ///////////////

      sealed trait Quote {
        val asInt: Int
      }
      object Quote {
        final case object Single extends Quote { val asInt = 1 }
        final case object Triple extends Quote { val asInt = 3 }
      }

      /////////////////
      //// Segment ////
      /////////////////

      sealed trait Segment[T]
      object Segment {

        //// Definition ////

        type Interpolated = _Interpolated[FAST]
        type Raw          = _Raw[FAST]
        sealed trait _Interpolated[T] extends Segment[T]
        sealed trait _Raw[T]          extends _Interpolated[T]

        type Plain = _Plain[FAST]
        type Expr  = _Expr[FAST]
        case class _Plain[T](value: String)   extends _Raw[T] with Phantom
        case class _Expr[T](value: Option[T]) extends _Interpolated[T]

        //// Instances ////

        object implicits extends implicits
        trait implicits {
          implicit def reprTxtSPlain[T]: Repr[_Plain[T]] = _.value
          implicit def reprTxtSExpr[T: Repr]: Repr[_Expr[T]] =
            R + '`' + _.value + '`'
          implicit def ftorTxtSPlain[T]:   Functor[_Plain]      = semi.functor
          implicit def ftorTxtSExpr[T]:    Functor[_Expr]       = semi.functor
          implicit def offZipTxtSExpr[T]:  OffsetZip[_Expr, T]  = _.map((0, _))
          implicit def offZipTxtSPlain[T]: OffsetZip[_Plain, T] = t => t.coerce
          implicit def reprTxtSRaw[T]: Repr[_Raw[T]] = {
            case t: _Plain[T] => t.repr
          }
          implicit def reprTxtSInt[T: Repr]: Repr[_Interpolated[T]] = {
            case t: _Plain[T] => t.repr
            case t: _Expr[T]  => t.repr
          }
          implicit def ftorTxtSRaw[T]: Functor[_Raw]          = semi.functor
          implicit def ftorTxtSInt[T]: Functor[_Interpolated] = semi.functor
          implicit def offZipTxtSRaw[T]: OffsetZip[_Raw, T] = {
            case t: _Plain[T] => OffsetZip(t)
          }
          implicit def offZipTxtSInt[T]: OffsetZip[_Interpolated, T] = {
            case t: _Plain[T] => OffsetZip(t)
            case t: _Expr[T]  => OffsetZip(t)
          }

          implicit def fromString[T](str: String): _Plain[T] = _Plain(str)
        }

//        trait Escape extends Interpolated
//        val Escape = text.Escape

      }

    }

  }

  //////////////////////////////////////////////////////////////////////////////
  //// App /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  //// Definition ////

  type App = AppOf[FAST]
  sealed trait AppOf[T] extends ASTOf[T]
  object App {

    //// Constructors ////

    type Prefix = PrefixOf[FAST]
    type Infix  = InfixOf[FAST]
    case class PrefixOf[T](_fn: T, off: Int, _arg: T) extends AppOf[T]
    case class InfixOf[T](_larg: T, loff: Int, opr: Opr, roff: Int, _rarg: T)
        extends AppOf[T]

    object Prefix {
      def unapply(t: Prefix) = Some((t.fn, t.arg))
      def apply(fn: FAST, off: Int, arg: FAST): Prefix = PrefixOf(fn, off, arg)
      def apply(fn: FAST, arg: FAST):           Prefix = Prefix(fn, 1, arg)
    }

    implicit def taggedPrefixOps(t: Tagged[Prefix]): PrefixOps = t.struct
    implicit class PrefixOps(t: Prefix) {
      def fn:                  TaggedAST = t._fn.unFix
      def arg:                 TaggedAST = t._arg.unFix
      def fn_=(v: TaggedAST):  Prefix    = t.copy(_fn = fix(v))
      def arg_=(v: TaggedAST): Prefix    = t.copy(_arg = fix(v))
    }

    object Infix {
      def unapply(t: Infix) = Some((t.larg, t.opr, t.rarg))
      def apply(larg: FAST, loff: Int, opr: Opr, roff: Int, rarg: FAST): Infix =
        InfixOf(larg, loff, opr, roff, rarg)
      def apply(larg: FAST, loff: Int, opr: Opr, rarg: FAST): Infix =
        Infix(larg, loff, opr, 1, rarg)
      def apply(larg: FAST, opr: Opr, roff: Int, rarg: FAST): Infix =
        Infix(larg, 1, opr, roff, rarg)
      def apply(larg: FAST, opr: Opr, rarg: FAST): Infix =
        Infix(larg, 1, opr, 1, rarg)
    }

    implicit def taggedInfixOps(t: Tagged[Infix]): InfixOps = t.struct
    implicit class InfixOps(t: Infix) {
      def larg:            FAST  = t._larg.unFix
      def rarg:            FAST  = t._rarg.unFix
      def opr:             FAST  = t.opr
      def larg_=(v: FAST): Infix = t.copy(_larg = v)
      def rarg_=(v: FAST): Infix = t.copy(_rarg = v)
      def opr_=(v: Opr):   Infix = t.copy(opr = v)
    }

    //// Instances ////

    object implicits extends implicits
    trait implicits {
      implicit def reprPrefix[T: Repr]: Repr[PrefixOf[T]] =
        t => R + t._fn + t.off + t._arg
      implicit def reprInfix[T: Repr]: Repr[InfixOf[T]] =
        t => R + t._larg + t.loff + t.opr + t.roff + t._rarg
      implicit def ftorPrefix: Functor[PrefixOf] = semi.functor
      implicit def ftorInfix:  Functor[InfixOf]  = semi.functor
      implicit def offZipPrefix[T: Repr]: OffsetZip[PrefixOf, T] =
        t => t.copy(_fn = (0, t._fn), _arg = (Repr.span(t._fn) + t.off, t._arg))
      implicit def offZipInfix[T: Repr]: OffsetZip[InfixOf, T] = t => {
        val rargSpan = (R + t._larg + t.loff + t.opr + t.roff).span
        t.copy(_larg = (0, t._larg), _rarg = (rargSpan, t._rarg))
      }
    }

    /////////////////
    //// Section ////
    /////////////////

    type Section = SectionOf[FAST]
    sealed trait SectionOf[T] extends AppOf[T]
    object Section {

      //// Constructors ////

      type Left  = LeftOf[FAST]
      type Right = RightOf[FAST]
      type Sides = SidesOf[FAST]

      case class LeftOf[T](_arg: T, off: Int, opr: Opr)  extends SectionOf[T]
      case class RightOf[T](opr: Opr, off: Int, _arg: T) extends SectionOf[T]
      case class SidesOf[T](opr: Opr)                    extends SectionOf[T] with Phantom

      object Left {
        def unapply(t: Left) = Some((t.arg, t.opr))
        def apply(arg: FAST, off: Int, opr: Opr): Left = LeftOf(arg, off, opr)
        def apply(arg: FAST, opr: Opr):           Left = Left(arg, 1, opr)
      }
      implicit def taggedLeftOps(t: Tagged[Left]): LeftOps = t.struct
      implicit class LeftOps(t: Left) {
        def arg            = t._arg.unFix
        def arg_=(v: FAST) = t.copy(_arg = v)
      }

      object Right {
        def unapply(t: Right) = Some((t.opr, t.arg))
        def apply(opr: Opr, off: Int, arg: FAST): Right = RightOf(opr, off, arg)
        def apply(opr: Opr, arg: FAST):           Right = Right(opr, 1, arg)
      }
      implicit def taggedRightOps(t: Tagged[Right]): RightOps = t.struct
      implicit class RightOps(t: Right) {
        def arg            = t._arg.unFix
        def arg_=(v: FAST) = t.copy(_arg = v)
      }

      object Sides {
        def unapply(t: Sides) = Some(t.opr)
        def apply(opr: Opr): Sides = SidesOf(opr)
      }
      implicit def taggedSidesOps(t: Tagged[Sides]): SidesOps = t.struct
      implicit class SidesOps(t: Sides) {
        def opr           = t.struct.opr
        def opr_=(v: Opr) = t.struct.copy(opr = v)
      }

      //// Instances ////

      object implicits extends implicits
      trait implicits {
        implicit def reprLeft[T: Repr]: Repr[LeftOf[T]] =
          t => R + t._arg + t.off + t.opr
        implicit def reprRight[T: Repr]: Repr[RightOf[T]] =
          t => R + t.opr + t.off + t._arg
        implicit def reprSides[T: Repr]: Repr[SidesOf[T]] =
          t => R + t.opr
        implicit def ftorLeft:  Functor[LeftOf]  = semi.functor
        implicit def ftorRight: Functor[RightOf] = semi.functor
        implicit def ftorSides: Functor[SidesOf] = semi.functor
        implicit def offZipLeft[T]: OffsetZip[LeftOf, T] =
          t => t.copy(_arg = (0, t._arg))
        implicit def offZipRight[T]: OffsetZip[RightOf, T] =
          t => t.copy(_arg = (Repr.span(t.opr) + t.off, t._arg))
        implicit def offZipSides[T]: OffsetZip[SidesOf, T] =
          t => t.coerce
      }
    }
  }

  ///

  //

  //

  //////////////////////////////////////////////////////////////////////////////
  //// Block ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  val newline = R + '\n'

  type Block = BlockOf[FAST]
  case class BlockOf[T](
    typ: Block.Type,
    indent: Int,
    emptyLines: List[Int],
    firstLine: Block.LineOf[T],
    lines: List[Block.LineOf[Option[T]]]
  ) extends ASTOf[T]

  object Block {
    sealed trait Type
    final case object Continuous    extends Type
    final case object Discontinuous extends Type

    def apply(
      typ: Type,
      indent: Int,
      emptyLines: List[Int],
      firstLine: LineOf[FAST],
      lines: List[LineOf[Option[FAST]]]
    ): Block = BlockOf(typ, indent, emptyLines, firstLine, lines)

    def apply(
      typ: Type,
      indent: Int,
      firstLine: LineOf[FAST],
      lines: List[LineOf[Option[FAST]]]
    ): Block = Block(typ, indent, List(), firstLine, lines)

    def unapply[T](
      t: BlockOf[T]
    ): Option[(Int, LineOf[T], List[LineOf[Option[T]]])] =
      Some((t.indent, t.firstLine, t.lines))

    //// Line ////

    type Line    = LineOf[FAST]
    type OptLine = LineOf[Option[FAST]]
    case class LineOf[+T](elem: T, off: Int)
    object Line {
      def apply[T](elem: T): LineOf[T] = LineOf(elem, 0)
    }

  }

  //////////////////////////////////////////////////////////////////////////////
  //// Module //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Module = _Module[FAST]
  case class _Module[T](lines: List1[Block.LineOf[T]]) extends ASTOf[T]

  object Module {
    import Block._
    def apply(ls: List1[Line]):         Module = _Module(ls)
    def apply(l: Line):                 Module = Module(List1(l))
    def apply(l: Line, ls: Line*):      Module = Module(List1(l, ls.to[List]))
    def apply(l: Line, ls: List[Line]): Module = Module(List1(l, ls))
  }

  def main() {

    import Ident.implicits._

    val foo    = Var("foo")
    val bar    = Var("foo")
    val plus   = Opr("+")
    val ttt2   = foo: AST
    val fooAST = foo: AST

    val foox = foo: AST

//    val foo    = Var("foo")
//    val foo2 = fix2(foo): FixedAST

//    println(foox.withNewID())
    val tfoo  = Var("foo")
    val tfoo2 = fixDeep(tfoo): FAST
    val tfoo3 = tfoo: FAST

    val l1 = Block.Line(tfoo3): Block.Line

    println("..........")
    println(tfoo2)
    println(tfoo3)

    val x1   = toTagged(foo)
    var app1 = App.Prefix(fooAST, 0, bar)

    fooAST match {
      case t: App        => println("It was APP 1")
      case t: App.Prefix => println("It was APP 2")
      case t: Ident      => println("It was Ident")
      case t: Var        => println("It was VAR")
    }
  }

  //
  //  //////////////////////////////////////////////////////////////////////////////
  //  //// Macro ///////////////////////////////////////////////////////////////////
  //  //////////////////////////////////////////////////////////////////////////////
  //
  //  sealed trait Macro extends AST
  //  object Macro {
  //
  //    import org.enso.syntax.text.ast.meta.Pattern
  //
  //    //// Matched ////
  //
  //     case class Match(
  //      pfx: Option[Pattern.Match],
  //      segs: Shifted.List1[Match.Segment],
  //      resolved: AST,
  //      id: Option[ID] = None
  //    ) extends Macro {
  //      val repr = {
  //        val pfxStream = pfx.map(_.toStream.reverse).getOrElse(List())
  //        val pfxRepr   = pfxStream.map(t => R + t.el + t.off)
  //        val segsRepr  = segs.map(_.repr)
  //        R + pfxRepr + segsRepr
  //      }
  //      def setID(newID: ID)                 = copy(id = Some(newID))
  //      def map(f: AST => AST)               = this
  //      def mapWithOff(f: (Int, AST) => AST) = this
  //      def path(): List1[AST] = segs.toList1().map(_.el.head)
  //    }
  //    object Match {
  //       case class Segment(head: Ident, body: Pattern.Match) {
  //        val repr = R + head + body
  //        def toStream: AST.Stream = ??? // Shifted(head) :: body.toStream
  //        def isValid:  Boolean    = body.isValid
  //        def map(f: Pattern.Match => Pattern.Match): Segment =
  //          copy(body = f(body))
  //      }
  //      object Segment {
  //        def apply(head: Ident): Segment = Segment(head, Pattern.Match.Nothing())
  //      }
  //    }
  //
  //    //// Ambiguous ////
  //
  //     case class Ambiguous(
  //      segs: Shifted.List1[Ambiguous.Segment],
  //      paths: Tree[AST, Unit],
  //      id: Option[ID] = None
  //    ) extends Macro {
  //      val repr                             = R + segs.map(_.repr)
  //      def setID(newID: ID)                 = copy(id = Some(newID))
  //      def map(f: AST => AST)               = this
  //      def mapWithOff(f: (Int, AST) => AST) = this
  //    }
  //    object Ambiguous {
  //       case class Segment(head: AST, body: Option[SAST]) extends Symbol {
  //        val repr = R + head + body
  //      }
  //      object Segment {
  //        def apply(head: AST): Segment = Segment(head, None)
  //      }
  //    }
  //
  //    //// Definition ////
  //
  //    type Definition = __Definition__
  //     case class __Definition__(
  //      back: Option[Pattern],
  //      init: List[Definition.Segment],
  //      last: Definition.LastSegment,
  //      fin: Definition.Finalizer
  //    ) {
  //      def path: List1[AST] = init.map(_.head) +: List1(last.head)
  //      def fwdPats: List1[Pattern] =
  //        init.map(_.pattern) +: List1(last.pattern.getOrElse(Pattern.Nothing()))
  //    }
  //    object Definition {
  //      import Pattern._
  //      type Finalizer = (Option[Pattern.Match], List[Macro.Match.Segment]) => AST
  //
  //       case class Segment(head: AST, pattern: Pattern) {
  //        def map(f: Pattern => Pattern): Segment = copy(pattern = f(pattern))
  //      }
  //      object Segment {
  //        type Tup = (AST, Pattern)
  //        def apply(t: Tup): Segment = Segment(t._1, t._2)
  //      }
  //
  //       case class LastSegment(head: AST, pattern: Option[Pattern]) {
  //        def map(f: Pattern => Pattern): LastSegment =
  //          copy(pattern = pattern.map(f))
  //      }
  //      object LastSegment {
  //        type Tup = (AST, Option[Pattern])
  //        def apply(t: Tup): LastSegment = LastSegment(t._1, t._2)
  //      }
  //
  //      def apply(back: Option[Pattern], t1: Segment.Tup, ts: List[Segment.Tup])(
  //        fin: Finalizer
  //      ): Definition = {
  //        val segs    = List1(t1, ts)
  //        val init    = segs.init
  //        val lastTup = segs.last
  //        val last    = (lastTup._1, Some(lastTup._2))
  //        Definition(back, init, last, fin)
  //      }
  //
  //      def apply(back: Option[Pattern], t1: Segment.Tup, ts: Segment.Tup*)(
  //        fin: Finalizer
  //      ): Definition = Definition(back, t1, ts.toList)(fin)
  //
  //      def apply(t1: Segment.Tup, t2_ : Segment.Tup*)(
  //        fin: Finalizer
  //      ): Definition = Definition(None, t1, t2_.toList)(fin)
  //
  //      def apply(initTups: List[Segment.Tup], lastHead: AST)(
  //        fin: Finalizer
  //      ): Definition =
  //        Definition(None, initTups, (lastHead, None), fin)
  //
  //      def apply(t1: Segment.Tup, last: AST)(fin: Finalizer): Definition =
  //        Definition(List(t1), last)(fin)
  //      //
  //      //      def apply(backPat: Option[Pattern], t1: Segment.Tup, ts: Segment.Tup*)(
  //      //        fin: Finalizer
  //      //      ): Definition =
  //      //        Definition(backPat, List1(t1, ts: _*), fin)
  //
  //      def apply(
  //        back: Option[Pattern],
  //        initTups: List[Segment.Tup],
  //        lastTup: LastSegment.Tup,
  //        fin: Finalizer
  //      ): Definition = {
  //        type PP = Pattern => Pattern
  //        val checkValid: PP = _ | ErrTillEnd("unmatched pattern")
  //        val checkFull: PP  = TillEndMarkUnmatched(_, "unmatched tokens")
  //
  //        val addInitChecks: List[Segment] => List[Segment] =
  //          _.map(_.map(checkValid).map(checkFull))
  //
  //        val addLastCheck: LastSegment => LastSegment =
  //          _.map(checkValid)
  //
  //        val initSegs           = initTups.map(Segment(_))
  //        val lastSeg            = LastSegment(lastTup)
  //        val backPatWithCheck   = back.map(checkValid)
  //        val initSegsWithChecks = addInitChecks(initSegs)
  //        val lastSegWithChecks  = addLastCheck(lastSeg)
  //        def finalizerWithChecks(
  //          pfx: Option[Pattern.Match],
  //          segs: List[Macro.Match.Segment]
  //        ) = {
  //          val pfxFail  = !pfx.forall(_.isValid)
  //          val segsFail = !segs.forall(_.isValid)
  //          if (pfxFail || segsFail) {
  //            val pfxStream  = pfx.map(_.toStream).getOrElse(List())
  //            val segsStream = segs.flatMap(_.toStream)
  //            val stream     = pfxStream ++ segsStream
  ////            AST.Unexpected("invalid statement", stream)
  //            ???
  //          } else fin(pfx, segs)
  //        }
  //        __Definition__(
  //          backPatWithCheck,
  //          initSegsWithChecks,
  //          lastSegWithChecks,
  //          finalizerWithChecks
  //        )
  //      }
  //
  //    }
  //  }
  //
  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //// Space - unaware AST /////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait SpacelessASTOf[T] extends ASTOf[T]

  implicit def ftorSlessAST[T]:   Functor[SpacelessASTOf]      = semi.functor
  implicit def offZipSlessAST[T]: OffsetZip[SpacelessASTOf, T] = _.map((0, _))

  //////////////////////////////////////////////////////////////////////////////
  /// Comment //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  sealed trait _Comment[T] extends SpacelessASTOf[T] with Phantom
  object Comment {
    val symbol = "#"

    type Disable    = _Disable[FAST]
    type SingleLine = _SingleLine[FAST]
    type MultiLine  = _MultiLine[FAST]

    case class _Disable[T](ast: T)                          extends _Comment[T]
    case class _SingleLine[T](text: String)                 extends _Comment[T]
    case class _MultiLine[T](off: Int, lines: List[String]) extends _Comment[T]

    //// Instances ////

    implicit def ftorCmmDis[T]: Functor[_Disable]    = semi.functor
    implicit def ftorCmmSL[T]:  Functor[_SingleLine] = semi.functor
    implicit def ftorCmmML[T]:  Functor[_MultiLine]  = semi.functor

    implicit def reprCmmDis[T: Repr]: Repr[_Disable[T]] =
      R + symbol + " " + _.ast
    implicit def reprCmmSL[T]: Repr[_SingleLine[T]] =
      R + symbol + symbol + _.text
    implicit def reprCmmML[T]: Repr[_MultiLine[T]] = t => {
      val commentBlock = t.lines match {
        case Nil => Nil
        case line +: lines =>
          val indentedLines = lines.map { s =>
            if (s.forall(_ == ' ')) newline + s
            else newline + 1 + t.off + s
          }
          (R + line) +: indentedLines
      }
      R + symbol + symbol + commentBlock
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Import //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Import = _Import[FAST]
  case class _Import[T](path: List1[Cons]) extends SpacelessASTOf[T]

  object Import {
    def apply(path: List1[Cons]):            Import = _Import(path)
    def apply(head: Cons):                   Import = Import(head, List())
    def apply(head: Cons, tail: List[Cons]): Import = Import(List1(head, tail))
    def apply(head: Cons, tail: Cons*):      Import = Import(head, tail.toList)

    object implicits extends implicits
    trait implicits {
      implicit def ftorImport[T]: Functor[_Import] = semi.functor
      implicit def reprImport[T]: Repr[_Import[T]] =
        t => R + ("import " + t.path.map(_.repr.show()).toList.mkString("."))
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Mixfix //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Mixfix = MixfixOf[FAST]
  case class MixfixOf[T](name: List1[Ident], args: List1[T])
      extends SpacelessASTOf[T]

  object Mixfix {
    object implicits extends implicits
    trait implicits {
      implicit def ftorMixfix[T]: Functor[MixfixOf] = semi.functor
      implicit def reprMixfix[T: Repr]: Repr[MixfixOf[T]] = t => {
        val lastRepr = if (t.name.length == t.args.length) List() else List(R)
        val argsRepr = t.args.toList.map(R + " " + _) ++ lastRepr
        val nameRepr = t.name.toList.map(Repr.of(_))
        R + (nameRepr, argsRepr).zipped.map(_ + _)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Group ///////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Group = GroupOf[FAST]
  case class GroupOf[T](body: Option[T]) extends SpacelessASTOf[T]

  object Group {
    def apply(body: Option[TaggedAST]): Group = Group(body)
    def apply(body: TaggedAST):         Group = Group(Some(body))
    def apply(body: SAST):              Group = Group(body.el)
    def apply():                        Group = Group(None)

    object implicits extends implicits
    trait implicits {
      implicit def ftorGrp[T]: Functor[GroupOf] = semi.functor
      implicit def reprGrp[T: Repr]: Repr[GroupOf[T]] =
        R + "(" + _.body + ")"
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Def /////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Def = DefOf[FAST]
  case class DefOf[T](name: Cons, args: List[T], body: Option[T])
      extends SpacelessASTOf[T]

  object Def {
    val symbol = "def"

    def apply(name: Cons):                   Def = Def(name, List())
    def apply(name: Cons, args: List[FAST]): Def = Def(name, args, None)
    def apply(name: Cons, args: List[FAST], body: Option[FAST]): Def =
      DefOf(name, args, body)

    object implicits extends implicits
    trait implicits {
      implicit def ftorDef[T]: Functor[DefOf] = semi.functor
      implicit def reprDef[T: Repr]: Repr[DefOf[T]] =
        t => R + symbol ++ t.name + t.args.map(R ++ _) + t.body
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  //// Foreign /////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////

  type Foreign = ForeignOf[FAST]
  case class ForeignOf[T](indent: Int, lang: String, code: List[String])
      extends SpacelessASTOf[T]

  object Foreign {
    object implicits extends implicits
    trait implicits {
      implicit def ftorForeign[T]: Functor[ForeignOf] = semi.functor
      implicit def reprForeign[T: Repr]: Repr[ForeignOf[T]] = t => {
        val code2 = t.code.map(R + t.indent + _).mkString("\n")
        R + "foreign " + t.lang + "\n" + code2
      }
    }
  }
}
