package scala

import scala.meta.projects.{Api => ProjectApi}
import scala.meta.syntactic.{Api => SyntacticApi}
import scala.meta.semantic.{Api => SemanticApi}
import scala.meta.tql.{Api => TQLApi}
import scala.meta.ui.{Api => UIApi}
import scala.meta.{Quasiquotes => QuasiquoteApi}

package object meta extends ProjectApi with SyntacticApi with SemanticApi with TQLApi with UIApi with QuasiquoteApi
