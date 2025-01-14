package org.enso.compiler.generate

import org.enso.data
import org.enso.data.Shifted.List1
import org.enso.interpreter.AstBlock
import org.enso.syntax.text.{AST, Debug}

// TODO [AA] Handle arbitrary parens

/** This object contains view patterns that allow matching on the parser AST for
  * more sophisticated constructs.
  *
  * These view patterns are implemented as custom unapply methods that only
  * return [[Some]] when more complex conditions are met.
  */
object AstView {
  object Block {
    def unapply(ast: AST): Option[(List[AST], AST)] = ast match {
      case AST.Block(_, _, firstLine, lines) =>
        val actualLines = firstLine.elem :: lines.flatMap(_.elem)
        if (actualLines.nonEmpty) {
          Some((actualLines.dropRight(1), actualLines.last))
        } else {
          None
        }
      case _ => None
    }
  }

  object Binding {
    val bindingOpSym = AST.Ident.Opr("=")

    def unapply(ast: AST): Option[(AST, AST)] = {
      ast match {
        case AST.App.Infix.any(ast) =>
          val left  = ast.larg
          val op    = ast.opr
          val right = ast.rarg

          if (op == bindingOpSym) {
            Some((left, right))
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object Assignment {
    val assignmentOpSym = AST.Ident.Opr("=")

    def unapply(ast: AST): Option[(AST.Ident.Var, AST)] = {
      ast match {
        case Binding(AST.Ident.Var.any(left), right) => Some((left, right))
        case _                                       => None
      }
    }
  }

  object Lambda {
    val lambdaOpSym = AST.Ident.Opr("->")

    def unapply(ast: AST): Option[(List[AST], AST)] = {
      ast match {
        case AST.App.Infix.any(ast) =>
          val left  = ast.larg
          val op    = ast.opr
          val right = ast.rarg

          if (op == lambdaOpSym) {
            left match {
              case LambdaParamList(args) => Some((args, right))
              case _                     => None
            }
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object PatternMatch {
    // Cons, args
    def unapply(ast: AST): Option[(AST.Ident.Cons, List[AST])] = {
      ast match {
        case SpacedList(AST.Ident.Cons.any(cons) :: xs) =>
          val realArgs = xs.collect { case a @ MatchParam(_) => a }

          if (realArgs.length == xs.length) {
            Some((cons, xs))
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object MatchParam {
    def unapply(ast: AST): Option[AST] = ast match {
      case DefinitionArgument(_) => Some(ast)
      case PatternMatch(_, _)    => Some(ast)
      case _                     => None
    }
  }

  object FunctionParam {
    def unapply(ast: AST): Option[AST] = ast match {
      case AssignedArgument(_, _) => Some(ast)
      case DefinitionArgument(_)  => Some(ast)
      case PatternMatch(_)        => Some(ast)
      case _                      => None
    }
  }

  object LambdaParamList {
    //TODO suspended arguments

    def unapply(ast: AST): Option[List[AST]] = {
      ast match {
        case SpacedList(args) =>
          val realArgs = args.collect { case a @ FunctionParam(_) => a }

          if (realArgs.length == args.length) {
            Some(args)
          } else {
            None
          }

        // TODO [AA] This really isn't true........
        case _ => Some(List(ast))
      }
    }
  }

  object MaybeParensed {
    def unapply(ast: AST): Option[AST] = {
      ast match {
        case AST.Group(mExpr) => mExpr.flatMap(unapply)
        case a                => Some(a)
      }
    }
  }

  /** Used for named and defaulted argument syntactic forms. */
  object AssignedArgument {
    def unapply(ast: AST): Option[(AST.Ident.Var, AST)] =
      MaybeParensed.unapply(ast).flatMap(Assignment.unapply)
  }

  object DefinitionArgument {
    def unapply(ast: AST): Option[AST.Ident.Var] = ast match {
      case AST.Ident.Var.any(ast) => Some(ast)
      case _                      => None
    }
  }

  /** Used for arguments declared as lazy. */
  object SuspendedArgument {}

  object Application {
    def unapply(ast: AST): Option[(AST, List[AST])] =
      SpacedList.unapply(ast).flatMap {
        case fun :: args =>
          fun match {
            case MethodCall(target, function, methodArgs) =>
              Some((function, target :: methodArgs ++ args))
            case _ => Some((fun, args))
          }
        case _ => None
      }
  }

  object MethodCall {
    def unapply(ast: AST): Option[(AST, AST.Ident, List[AST])] = ast match {
      case OperatorDot(target, Application(ConsOrVar(ident), args)) =>
        Some((target, ident, args))
      case OperatorDot(target, ConsOrVar(ident)) =>
        Some((target, ident, List()))
      case _ => None
    }
  }

  object SpacedList {

    /**
      *
      * @param ast
      * @return the constructor, and a list of its arguments
      */
    def unapply(ast: AST): Option[List[AST]] = {
      matchSpacedList(ast)
    }

    def matchSpacedList(ast: AST): Option[List[AST]] = {
      ast match {
        case AST.App.Prefix(fn, arg) =>
          val fnRecurse = matchSpacedList(fn)

          fnRecurse match {
            case Some(headItems) => Some(headItems :+ arg)
            case None            => Some(List(fn, arg))
          }

        case _ => None
      }
    }
  }

  object MethodDefinition {
    def unapply(ast: AST): Option[(List[AST], AST, AST)] = {
      ast match {
        case Binding(lhs, rhs) =>
          lhs match {
            case MethodReference(targetPath, name) =>
              Some((targetPath, name, rhs))
            case _ =>
              None
          }
        case _ =>
          None
      }
    }
  }

  object ConsOrVar {
    def unapply(arg: AST): Option[AST.Ident] = arg match {
      case AST.Ident.Var.any(arg)  => Some(arg)
      case AST.Ident.Cons.any(arg) => Some(arg)
      case _                       => None
    }
  }

  object OperatorDot {
    def unapply(ast: AST): Option[(AST, AST)] = ast match {
      case AST.App.Infix(left, AST.Ident.Opr("."), right) => Some((left, right))
      case _ =>
        None
    }
  }

  object Path {

    def unapply(ast: AST): Option[List[AST]] = {
      val path = matchPath(ast)

      if (path.isEmpty) {
        None
      } else {
        Some(path)
      }
    }

    def matchPath(ast: AST): List[AST] = {
      ast match {
        case OperatorDot(left, right) =>
          right match {
            case AST.Ident.any(right) => matchPath(left) :+ right
            case _                    => List()
          }
        case AST.Ident.any(ast) => List(ast)
        case _                  => List()
      }
    }
  }

  object MethodReference {
    def unapply(ast: AST): Option[(List[AST], AST)] = {
      ast match {
        case Path(segments) =>
          if (segments.length >= 2) {
            val consPath = segments.dropRight(1)
            val maybeVar = segments.last

            val isValid = consPath.collect {
                case a @ AST.Ident.Cons(_) => a
              }.length == consPath.length

            if (isValid) {
              maybeVar match {
                case AST.Ident.Var(_) => Some((consPath, maybeVar))
                case _                => None
              }
            } else {
              None
            }
          } else {
            None
          }
        case _ => None
      }
    }
  }

  object CaseBranch {
    // TODO [AA]
  }

  object CaseExpression {
    val caseName = data.List1(AST.Ident.Var("case"), AST.Ident.Var("of"))

    // scrutinee and branches
    def unapply(ast: AST): Option[(AST, List[AST])] = {
      ast match {
        case AST.Mixfix(identSegments, argSegments) =>
          if (identSegments == caseName) {
            println("==== MIXFIX ====")
            println(Debug.pretty(ast.toString))

            None
          } else {
            None
          }
        case _ => None
      }
    }
  }
}
