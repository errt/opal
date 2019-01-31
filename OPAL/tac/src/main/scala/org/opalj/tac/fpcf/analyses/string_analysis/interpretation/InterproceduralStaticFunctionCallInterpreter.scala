/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis.interpretation

import scala.collection.mutable.ListBuffer

import org.opalj.fpcf.FinalEP
import org.opalj.fpcf.InterimResult
import org.opalj.fpcf.ProperOnUpdateContinuation
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Result
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.cfg.CFG
import org.opalj.br.fpcf.properties.StringConstancyProperty
import org.opalj.tac.StaticFunctionCall
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.fpcf.analyses.string_analysis.ComputationState
import org.opalj.tac.fpcf.analyses.string_analysis.V
import org.opalj.tac.ReturnValue

/**
 * The `InterproceduralStaticFunctionCallInterpreter` is responsible for processing
 * [[StaticFunctionCall]]s in an interprocedural fashion.
 * <p>
 * For supported method calls, see the documentation of the `interpret` function.
 *
 * @see [[AbstractStringInterpreter]]
 *
 * @author Patrick Mell
 */
class InterproceduralStaticFunctionCallInterpreter(
        cfg:             CFG[Stmt[V], TACStmts[V]],
        exprHandler:     InterproceduralInterpretationHandler,
        ps:              PropertyStore,
        state:           ComputationState,
        declaredMethods: DeclaredMethods,
        c:               ProperOnUpdateContinuation
) extends AbstractStringInterpreter(cfg, exprHandler) {

    override type T = StaticFunctionCall[V]

    /**
     * This function always returns a list with a single element consisting of
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyLevel.DYNAMIC]],
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyType.APPEND]], and
     * [[org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation.UnknownWordSymbol]].
     *
     * @note For this implementation, `defSite` plays a role!
     *
     * @see [[AbstractStringInterpreter.interpret]]
     */
    override def interpret(instr: T, defSite: Int): ProperPropertyComputationResult = {
        val m = getDeclaredMethod(declaredMethods, instr.name)
        val tac = getTACAI(ps, m, state)
        if (tac.isDefined) {
            // TAC available => Get return UVar and start the string analysis
            val ret = tac.get.stmts.find(_.isInstanceOf[ReturnValue[V]]).get
            val uvar = ret.asInstanceOf[ReturnValue[V]].expr.asVar
            val entity = (uvar, m)

            val eps = ps(entity, StringConstancyProperty.key)
            eps match {
                case FinalEP(e, p) ⇒
                    Result(e, p)
                case _ ⇒
                    if (!state.dependees.contains(m)) {
                        state.dependees(m) = ListBuffer()
                    }
                    state.dependees(m).append(eps)
                    state.var2IndexMapping(uvar) = defSite
                    InterimResult(
                        entity,
                        StringConstancyProperty.lowerBound,
                        StringConstancyProperty.upperBound,
                        List(),
                        c
                    )
            }
        } else {
            // No TAC => Register dependee and continue
            InterimResult(
                m,
                StringConstancyProperty.lowerBound,
                StringConstancyProperty.upperBound,
                state.dependees.values.flatten,
                c
            )
        }
    }

}
