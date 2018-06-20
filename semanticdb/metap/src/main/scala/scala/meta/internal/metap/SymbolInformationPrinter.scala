package scala.meta.internal.metap

import scala.collection.mutable
import scala.math.Ordering
import scala.meta.internal.semanticdb._
import scala.meta.internal.semanticdb.Accessibility.Tag._
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SingletonType.Tag._
import scala.meta.internal.semanticdb.SymbolInformation._
import scala.meta.internal.semanticdb.SymbolInformation.Kind._
import scala.meta.internal.semanticdb.SymbolInformation.Property._

trait SymbolInformationPrinter extends BasePrinter {
  def pprint(info: SymbolInformation): Unit = {
    out.print(info.symbol)
    out.print(" => ")

    val infoNotes = new InfoNotes
    val infoPrinter = new InfoPrinter(infoNotes)
    infoPrinter.pprint(info)
    out.println()

    if (settings.format.isDetailed) {
      val printed = mutable.Set[String]()
      infoNotes.visited.tail.foreach { info =>
        if (!printed(info.symbol)) {
          printed += info.symbol
          out.print("  ")
          out.print(info.name)
          out.print(" => ")
          out.println(info.symbol)
        }
      }
    }
  }

  private class InfoPrinter(notes: InfoNotes) {
    def pprint(info: SymbolInformation): Unit = {
      notes.visit(info)
      rep(info.annotations, " ", " ")(pprint)
      opt(info.accessibility)(pprint)
      if (info.has(ABSTRACT)) out.print("abstract ")
      if (info.has(FINAL)) out.print("final ")
      if (info.has(SEALED)) out.print("sealed ")
      if (info.has(IMPLICIT)) out.print("implicit ")
      if (info.has(LAZY)) out.print("lazy ")
      if (info.has(CASE)) out.print("case ")
      if (info.has(COVARIANT)) out.print("covariant ")
      if (info.has(CONTRAVARIANT)) out.print("contravariant ")
      if (info.has(VAL)) out.print("val ")
      if (info.has(VAR)) out.print("var ")
      if (info.has(STATIC)) out.print("static ")
      if (info.has(PRIMARY)) out.print("primary ")
      if (info.has(ENUM)) out.print("enum ")
      info.kind match {
        case LOCAL => out.print("local ")
        case FIELD => out.print("field ")
        case METHOD => out.print("method ")
        case CONSTRUCTOR => out.print("ctor ")
        case MACRO => out.print("macro ")
        case TYPE => out.print("type ")
        case PARAMETER => out.print("param ")
        case SELF_PARAMETER => out.print("selfparam ")
        case TYPE_PARAMETER => out.print("typeparam ")
        case OBJECT => out.print("object ")
        case PACKAGE => out.print("package ")
        case PACKAGE_OBJECT => out.print("package object ")
        case CLASS => out.print("class ")
        case TRAIT => out.print("trait ")
        case INTERFACE => out.print("interface ")
        case UNKNOWN_KIND | Kind.Unrecognized(_) => out.print("unknown ")
      }
      pprint(info.name)
      opt(info.prefixBeforeTpe, info.tpe)(pprint)
    }

    private def pprint(ann: Annotation): Unit = {
      out.print("@")
      ann.tpe match {
        case NoType =>
          out.print("<?>")
        case tpe =>
          pprint(tpe)
      }
    }

    private def pprint(acc: Accessibility): Unit = {
      acc.tag match {
        case PUBLIC =>
          out.print("")
        case PRIVATE =>
          out.print("private ")
        case PRIVATE_THIS =>
          out.print("private[this] ")
        case PRIVATE_WITHIN =>
          out.print("private[")
          pprint(acc.symbol, Reference)
          out.print("] ")
        case PROTECTED =>
          out.print("protected ")
        case PROTECTED_THIS =>
          out.print("protected[this] ")
        case PROTECTED_WITHIN =>
          out.print("protected[")
          pprint(acc.symbol, Reference)
          out.print("] ")
        case UNKNOWN_ACCESSIBILITY | Accessibility.Tag.Unrecognized(_) =>
          out.print("<?>")
      }
    }

    def pprint(tpe: Type): Unit = {
      def ref(sym: String): Unit = {
        pprint(sym, Reference)
      }
      def defn(info: SymbolInformation): Unit = {
        notes.discover(info)
        pprint(info.symbol, Definition)
      }
      def prefix(tpe: Type): Unit = {
        tpe match {
          case TypeRef(pre, sym, args) =>
            pre match {
              case _: SingletonType =>
                prefix(pre)
                out.print(".")
              case NoType =>
                ()
              case _ =>
                prefix(pre)
                out.print("#")
            }
            ref(sym)
            rep("[", args, ", ", "]")(normal)
          case SingletonType(tag, pre, sym, x, s) =>
            tag match {
              case SYMBOL =>
                opt(pre, ".")(prefix)
                ref(sym)
              case THIS =>
                opt(sym, ".")(ref)
                out.print("this")
              case SUPER =>
                opt(pre, ".")(prefix)
                out.print("super")
                opt("[", sym, "]")(ref)
              case UNIT =>
                out.print("()")
              case BOOLEAN =>
                if (x == 0) out.print("false")
                else if (x == 1) out.print("true")
                else out.print("<?>")
              case BYTE | SHORT =>
                out.print(x)
              case CHAR =>
                out.print("'" + x.toChar + "'")
              case INT =>
                out.print(x)
              case LONG =>
                out.print(x + "L")
              case FLOAT =>
                out.print(java.lang.Float.intBitsToFloat(x.toInt) + "f")
              case DOUBLE =>
                out.print(java.lang.Double.longBitsToDouble(x))
              case STRING =>
                out.print("\"" + s + "\"")
              case NULL =>
                out.print("null")
              case UNKNOWN_SINGLETON | SingletonType.Tag.Unrecognized(_) =>
                out.print("<?>")
            }
          case IntersectionType(types) =>
            rep(types, " & ")(normal)
          case UnionType(types) =>
            rep(types, " | ")(normal)
          case WithType(types) =>
            rep(types, " with ")(normal)
          case StructuralType(utpe, decls) =>
            decls.infos.foreach(notes.discover)
            opt(utpe)(normal)
            if (decls.infos.nonEmpty) rep(" { ", decls.infos, "; ", " }")(defn)
            else out.print(" {}")
          case AnnotatedType(anns, utpe) =>
            opt(utpe)(normal)
            out.print(" ")
            rep(anns, " ", "")(pprint)
          case ExistentialType(utpe, decls) =>
            decls.infos.foreach(notes.discover)
            opt(utpe)(normal)
            rep(" forSome { ", decls.infos, "; ", " }")(defn)
          case UniversalType(tparams, utpe) =>
            tparams.infos.foreach(notes.discover)
            rep("[", tparams.infos, ", ", "] => ")(defn)
            opt(utpe)(normal)
          case ClassInfoType(tparams, parents, decls) =>
            rep("[", tparams.infos, ", ", "]")(defn)
            rep(" extends ", parents, " with ")(normal)
            if (decls.infos.nonEmpty) out.print(s" { +${decls.infos.length} decls }")
          case MethodType(tparams, paramss, res) =>
            rep("[", tparams.infos, ", ", "]")(defn)
            rep("(", paramss, ")(", ")")(params => rep(params.infos, ", ")(defn))
            opt(": ", res)(normal)
          case ByNameType(utpe) =>
            out.print("=> ")
            opt(utpe)(normal)
          case RepeatedType(utpe) =>
            opt(utpe)(normal)
            out.print("*")
          case TypeType(tparams, lo, hi) =>
            rep("[", tparams.infos, ", ", "]")(defn)
            if (lo != hi) {
              lo match {
                case NothingTpe() => ()
                case lo => opt(" >: ", lo)(normal)
              }
              hi match {
                case AnyTpe() => ()
                case hi => opt(" <: ", hi)(normal)
              }
            } else {
              val alias = lo
              opt(" = ", alias)(normal)
            }
          case NoType =>
            out.print("<?>")
        }
      }
      def normal(tpe: Type): Unit = {
        tpe match {
          case SingletonType(tag, _, _, _, _) =>
            tag match {
              case SYMBOL | THIS | SUPER =>
                prefix(tpe)
                out.print(".type")
              case _ =>
                prefix(tpe)
            }
          case _ =>
            prefix(tpe)
        }
      }
      normal(tpe)
    }

    private sealed trait SymbolStyle
    private case object Reference extends SymbolStyle
    private case object Definition extends SymbolStyle

    private def pprint(sym: String, style: SymbolStyle): Unit = {
      val info = notes.visit(sym)
      style match {
        case Reference =>
          pprint(info.name)
        case Definition =>
          // NOTE: I am aware of some degree of duplication with pprint(info).
          // However, deduplicating these two methods leads to very involved code,
          // since there are subtle differences in behavior.
          rep(info.annotations, " ", " ")(pprint)
          opt(info.accessibility)(pprint)
          if (info.has(ABSTRACT) && info.kind == CLASS) out.print("abstract ")
          if (info.has(FINAL) && info.kind != OBJECT) out.print("final ")
          if (info.has(SEALED)) out.print("sealed ")
          if (info.has(IMPLICIT)) out.print("implicit ")
          if (info.has(LAZY)) out.print("lazy ")
          if (info.has(CASE)) out.print("case ")
          if (info.has(COVARIANT)) out.print("+")
          if (info.has(CONTRAVARIANT)) out.print("-")
          if (info.has(VAL)) out.print("val ")
          if (info.has(VAR)) out.print("var ")
          if (info.has(STATIC)) out.print("static ")
          if (info.has(PRIMARY)) out.print("")
          if (info.has(ENUM)) out.print("enum ")
          info.kind match {
            case LOCAL => out.print("")
            case FIELD => out.print("")
            case METHOD => out.print("def ")
            case CONSTRUCTOR => out.print("def ")
            case MACRO => out.print("macro ")
            case TYPE => out.print("type ")
            case PARAMETER => out.print("")
            case SELF_PARAMETER => out.print("")
            case TYPE_PARAMETER => out.print("")
            case OBJECT => out.print("object ")
            case PACKAGE => out.print("package ")
            case PACKAGE_OBJECT => out.print("package object ")
            case CLASS => out.print("class ")
            case TRAIT => out.print("trait ")
            case INTERFACE => out.print("interface ")
            case UNKNOWN_KIND | Kind.Unrecognized(_) => out.print("unknown ")
          }
          pprint(info.name)
          opt(info.prefixBeforeTpe, info.tpe)(pprint)
      }
    }

    private def pprint(name: String): Unit = {
      if (name.nonEmpty) out.print(name)
      else out.print("<?>")
    }

    private implicit class InfoOps(info: SymbolInformation) {
      def prefixBeforeTpe: String = {
        info.kind match {
          case LOCAL | FIELD | PARAMETER | SELF_PARAMETER | UNKNOWN_KIND | Kind.Unrecognized(_) =>
            ": "
          case METHOD | CONSTRUCTOR | MACRO | TYPE | TYPE_PARAMETER | OBJECT | PACKAGE |
              PACKAGE_OBJECT | CLASS | TRAIT | INTERFACE =>
            ""
        }
      }
    }

    private object NothingTpe {
      def unapply(tpe: Type): Boolean = tpe match {
        case TypeRef(NoType, "scala.Nothing#", Nil) => true
        case _ => false
      }
    }

    private object AnyTpe {
      def unapply(tpe: Type): Boolean = tpe match {
        case TypeRef(NoType, "scala.Any#", Nil) => true
        case _ => false
      }
    }
  }

  private lazy val docSymtab: Map[String, SymbolInformation] = {
    doc.symbols.map(info => (info.symbol, info)).toMap
  }

  private class InfoNotes {
    private val buf = mutable.ListBuffer[SymbolInformation]()
    private val noteSymtab = mutable.Map[String, SymbolInformation]()

    def discover(info: SymbolInformation): Unit = {
      if (!docSymtab.contains(info.symbol) && info.kind != UNKNOWN_KIND) {
        noteSymtab(info.symbol) = info
      }
    }

    def visit(sym: String): SymbolInformation = {
      val symtabInfo = noteSymtab.get(sym).orElse(docSymtab.get(sym))
      val info = symtabInfo.getOrElse {
        val name = if (sym.isGlobal) sym.desc.name else sym
        SymbolInformation(symbol = sym, name = name)
      }
      visit(info)
    }

    def visit(info: SymbolInformation): SymbolInformation = {
      buf.append(info)
      info
    }

    def visited: List[SymbolInformation] = {
      buf.toList
    }
  }

  implicit def infoOrder: Ordering[SymbolInformation] = {
    Ordering.by(_.symbol)
  }
}
