package scala.meta.internal.semanticdb.scalac

import scala.meta.internal.scalacp._
import scala.meta.internal.{semanticdb => s}
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SingletonType.{Tag => st}
import scala.reflect.internal.{Flags => gf}

trait TypeOps { self: SemanticdbOps =>
  implicit class XtensionGTypeSType(gtpe: g.Type) {
    def toSemantic(linkMode: LinkMode): s.Type = {
      def loop(gtpe: g.Type): s.Type = {
        gtpe match {
          case ByNameType(gtpe) =>
            val stpe = loop(gtpe)
            s.ByNameType(stpe)
          case RepeatedType(gtpe) =>
            val stpe = loop(gtpe)
            s.RepeatedType(stpe)
          case g.TypeRef(gpre, gsym, gargs) =>
            val spre = if (gtpe.hasNontrivialPrefix) loop(gpre) else s.NoType
            val ssym = gsym.ssym
            val sargs = gargs.map(loop)
            s.TypeRef(spre, ssym, sargs)
          case g.SingleType(gpre, gsym) =>
            val stag = st.SYMBOL
            val spre = if (gtpe.hasNontrivialPrefix) loop(gpre) else s.NoType
            val ssym = gsym.ssym
            s.SingletonType(stag, spre, ssym, 0, "")
          case g.ThisType(gsym) =>
            val stag = st.THIS
            val ssym = gsym.ssym
            s.SingletonType(stag, s.NoType, ssym, 0, "")
          case g.SuperType(gpre, gmix) =>
            val stag = st.SUPER
            val spre = loop(gpre.typeSymbol.tpe)
            val ssym = gmix.typeSymbol.ssym
            s.SingletonType(stag, spre, ssym, 0, "")
          case g.ConstantType(g.Constant(sym: g.TermSymbol)) if sym.hasFlag(gf.JAVA_ENUM) =>
            loop(g.SingleType(sym.owner.thisPrefix, sym))
          case g.ConstantType(g.Constant(_: g.Type)) =>
            loop(gtpe.widen)
          case g.ConstantType(gconst) =>
            def floatBits(x: Float) = java.lang.Float.floatToRawIntBits(x).toLong
            def doubleBits(x: Double) = java.lang.Double.doubleToRawLongBits(x)
            gconst.value match {
              case () =>
                s.SingletonType(st.UNIT, s.NoType, "", 0, "")
              case false =>
                s.SingletonType(st.BOOLEAN, s.NoType, "", 0, "")
              case true =>
                s.SingletonType(st.BOOLEAN, s.NoType, "", 1, "")
              case x: Byte =>
                s.SingletonType(st.BYTE, s.NoType, "", x.toLong, "")
              case x: Short =>
                s.SingletonType(st.SHORT, s.NoType, "", x.toLong, "")
              case x: Char =>
                s.SingletonType(st.CHAR, s.NoType, "", x.toLong, "")
              case x: Int =>
                s.SingletonType(st.INT, s.NoType, "", x.toLong, "")
              case x: Long =>
                s.SingletonType(st.LONG, s.NoType, "", x, "")
              case x: Float =>
                s.SingletonType(st.FLOAT, s.NoType, "", floatBits(x), "")
              case x: Double =>
                s.SingletonType(st.DOUBLE, s.NoType, "", doubleBits(x), "")
              case x: String =>
                s.SingletonType(st.STRING, s.NoType, "", 0, x)
              case null =>
                s.SingletonType(st.NULL, s.NoType, "", 0, "")
              case _ =>
                sys.error(s"unsupported const ${gconst}: ${g.showRaw(gconst)}")
            }
          case g.RefinedType(gparents, gdecls) =>
            val sparents = gparents.map(loop)
            val stpe = s.WithType(sparents)
            val sdecls = Some(gdecls.semanticdbDecls.sscope(HardlinkChildren))
            s.StructuralType(stpe, sdecls)
          case g.AnnotatedType(ganns, gtpe) =>
            val sanns = ganns.reverse.map(_.toSemantic)
            val stpe = loop(gtpe)
            s.AnnotatedType(sanns, stpe)
          case g.ExistentialType(gtparams, gtpe) =>
            val stpe = loop(gtpe)
            val sdecls = Some(gtparams.sscope(HardlinkChildren))
            s.ExistentialType(stpe, sdecls)
          case g.ClassInfoType(gparents, _, gclass) =>
            val stparams = Some(s.Scope())
            val sparents = gparents.map(loop)
            val sdecls = Some(gclass.semanticdbDecls.sscope(linkMode))
            s.ClassInfoType(stparams, sparents, sdecls)
          case g.NullaryMethodType(gtpe) =>
            val stparams = Some(s.Scope())
            val stpe = loop(gtpe)
            s.MethodType(stparams, Nil, stpe)
          case gtpe: g.MethodType =>
            def flatten(gtpe: g.Type): (List[List[g.Symbol]], g.Type) = {
              gtpe match {
                case g.MethodType(ghead, gtpe) =>
                  val (gtail, gret) = flatten(gtpe)
                  (ghead :: gtail, gret)
                case gother =>
                  (Nil, gother)
              }
            }
            val (gparamss, gret) = flatten(gtpe)
            val stparams = Some(s.Scope())
            val sparamss = gparamss.map(_.sscope(linkMode))
            val sret = loop(gret)
            s.MethodType(stparams, sparamss, sret)
          case g.TypeBounds(glo, ghi) =>
            val stparams = Some(s.Scope())
            val slo = loop(glo)
            val shi = loop(ghi)
            s.TypeType(stparams, slo, shi)
          case g.PolyType(gtparams, gtpe) =>
            loop(gtpe) match {
              case s.NoType => s.NoType
              case t: s.ClassInfoType =>
                val stparams = gtparams.sscope(linkMode)
                t.copy(typeParameters = Some(stparams))
              case t: s.MethodType =>
                val stparams = gtparams.sscope(linkMode)
                t.copy(typeParameters = Some(stparams))
              case t: s.TypeType =>
                val stparams = gtparams.sscope(linkMode)
                t.copy(typeParameters = Some(stparams))
              case stpe =>
                val stparams = gtparams.sscope(HardlinkChildren)
                s.UniversalType(Some(stparams), stpe)
            }
          case g.NoType =>
            s.NoType
          case g.NoPrefix =>
            s.NoType
          case g.ErrorType =>
            s.NoType
          case gother =>
            sys.error(s"unsupported type ${gother}: ${g.showRaw(gother)}")
        }
      }
      loop(gtpe)
    }
  }

  implicit class XtensionGType(gtpe: g.Type) {
    // FIXME: https://github.com/scalameta/scalameta/issues/1343
    def hasNontrivialPrefix: Boolean = {
      val (gpre, gsym) = {
        gtpe match {
          case g.TypeRef(gpre, gsym, _) => (gpre, gsym)
          case g.SingleType(gpre, gsym) => (gpre, gsym)
          case _ => return true
        }
      }
      gpre match {
        case g.SingleType(_, gpresym) =>
          gpresym.isTerm && !gpresym.isModule
        case g.ThisType(gpresym) =>
          !gpresym.hasPackageFlag && !gpresym.isModuleOrModuleClass && !gpresym.isConstructor
        case _ =>
          true
      }
    }
  }

  object ByNameType {
    def unapply(gtpe: g.Type): Option[g.Type] = gtpe match {
      case g.TypeRef(_, g.definitions.ByNameParamClass, garg :: Nil) => Some(garg)
      case _ => None
    }
  }

  object RepeatedType {
    def unapply(gtpe: g.Type): Option[g.Type] = gtpe match {
      case g.TypeRef(_, g.definitions.RepeatedParamClass, garg :: Nil) => Some(garg)
      case g.TypeRef(_, g.definitions.JavaRepeatedParamClass, garg :: Nil) => Some(garg)
      case _ => None
    }
  }
}
