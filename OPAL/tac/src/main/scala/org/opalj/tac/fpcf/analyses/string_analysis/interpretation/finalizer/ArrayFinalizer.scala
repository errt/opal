/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis.interpretation.finalizer

import org.opalj.br.cfg.CFG
import org.opalj.br.fpcf.properties.string_definition.StringConstancyInformation
import org.opalj.tac.fpcf.analyses.string_analysis.ComputationState
import org.opalj.tac.fpcf.analyses.string_analysis.V
import org.opalj.tac.ArrayLoad
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.fpcf.analyses.string_analysis.interpretation.interprocedural.ArrayPreparationInterpreter

/**
 * @author Patrick Mell
 */
class ArrayFinalizer(cfg: CFG[Stmt[V], TACStmts[V]], state: ComputationState) {

    type T = ArrayLoad[V]

    def interpret(
        instr: T, defSite: Int
    ): Unit = {
        val allDefSites = ArrayPreparationInterpreter.getStoreAndLoadDefSites(instr, cfg)
        state.fpe2sci(defSite) = StringConstancyInformation.reduceMultiple(
            allDefSites.sorted.map { state.fpe2sci(_) }
        )
    }

}
