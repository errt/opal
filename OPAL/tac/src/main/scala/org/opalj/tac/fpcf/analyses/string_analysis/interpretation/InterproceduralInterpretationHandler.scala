/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.tac.fpcf.analyses.string_analysis.interpretation

import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Result
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.cfg.CFG
import org.opalj.br.fpcf.cg.properties.Callees
import org.opalj.br.fpcf.properties.StringConstancyProperty
import org.opalj.tac.Stmt
import org.opalj.tac.TACStmts
import org.opalj.tac.fpcf.analyses.string_analysis.V
import org.opalj.tac.ArrayLoad
import org.opalj.tac.Assignment
import org.opalj.tac.BinaryExpr
import org.opalj.tac.ExprStmt
import org.opalj.tac.GetField
import org.opalj.tac.IntConst
import org.opalj.tac.New
import org.opalj.tac.NonVirtualFunctionCall
import org.opalj.tac.NonVirtualMethodCall
import org.opalj.tac.StaticFunctionCall
import org.opalj.tac.StringConst
import org.opalj.tac.VirtualFunctionCall
import org.opalj.tac.VirtualMethodCall

/**
 * `InterproceduralInterpretationHandler` is responsible for processing expressions that are
 * relevant in order to determine which value(s) a string read operation might have. These
 * expressions usually come from the definitions sites of the variable of interest.
 * <p>
 * For this interpretation handler used interpreters (concrete instances of
 * [[AbstractStringInterpreter]]) can either return a final or intermediate result.
 *
 * @param cfg The control flow graph that underlies the program / method in which the expressions of
 *            interest reside.
 *
 * @author Patrick Mell
 */
class InterproceduralInterpretationHandler(
        cfg:             CFG[Stmt[V], TACStmts[V]],
        ps:              PropertyStore,
        declaredMethods: DeclaredMethods,
        callees:         Callees
) extends InterpretationHandler(cfg) {

    /**
     * Processed the given definition site in an interprocedural fashion.
     * <p>
     * @inheritdoc
     */
    override def processDefSite(defSite: Int): ProperPropertyComputationResult = {
        // Without doing the following conversion, the following compile error will occur: "the
        // result type of an implicit conversion must be more specific than org.opalj.fpcf.Entity"
        val e: Integer = defSite.toInt
        // Function parameters are not evaluated but regarded as unknown
        if (defSite < 0) {
            return Result(e, StringConstancyProperty.lowerBound)
        } else if (processedDefSites.contains(defSite)) {
            return Result(e, StringConstancyProperty.getNeutralElement)
        }
        processedDefSites.append(defSite)

        val result = stmts(defSite) match {
            case Assignment(_, _, expr: StringConst) ⇒
                new StringConstInterpreter(cfg, this).interpret(expr)
            case Assignment(_, _, expr: IntConst) ⇒
                new IntegerValueInterpreter(cfg, this).interpret(expr)
            case Assignment(_, _, expr: ArrayLoad[V]) ⇒
                new InterproceduralArrayInterpreter(cfg, this, callees).interpret(expr)
            case Assignment(_, _, expr: New) ⇒
                new NewInterpreter(cfg, this).interpret(expr)
            case Assignment(_, _, expr: VirtualFunctionCall[V]) ⇒
                new InterproceduralVirtualFunctionCallInterpreter(
                    cfg, this, callees
                ).interpret(expr)
            case Assignment(_, _, expr: StaticFunctionCall[V]) ⇒
                new InterproceduralStaticFunctionCallInterpreter(cfg, this, callees).interpret(expr)
            case Assignment(_, _, expr: BinaryExpr[V]) ⇒
                new BinaryExprInterpreter(cfg, this).interpret(expr)
            case Assignment(_, _, expr: NonVirtualFunctionCall[V]) ⇒
                new InterproceduralNonVirtualFunctionCallInterpreter(
                    cfg, this, callees
                ).interpret(expr)
            case Assignment(_, _, expr: GetField[V]) ⇒
                new InterproceduralFieldInterpreter(cfg, this, callees).interpret(expr)
            case ExprStmt(_, expr: VirtualFunctionCall[V]) ⇒
                new InterproceduralVirtualFunctionCallInterpreter(
                    cfg, this, callees
                ).interpret(expr)
            case ExprStmt(_, expr: StaticFunctionCall[V]) ⇒
                new InterproceduralStaticFunctionCallInterpreter(cfg, this, callees).interpret(expr)
            case vmc: VirtualMethodCall[V] ⇒
                new InterproceduralVirtualMethodCallInterpreter(cfg, this, callees).interpret(vmc)
            case nvmc: NonVirtualMethodCall[V] ⇒
                new InterproceduralNonVirtualMethodCallInterpreter(
                    cfg, this, callees
                ).interpret(nvmc)
            case _ ⇒ Result(e, StringConstancyProperty.getNeutralElement)
        }
        // Replace the entity of the result
        Result(e, result.asInstanceOf[StringConstancyProperty])
    }

}

object InterproceduralInterpretationHandler {

    /**
     * @see [[IntraproceduralInterpretationHandler]]
     */
    def apply(
        cfg:             CFG[Stmt[V], TACStmts[V]],
        ps:              PropertyStore,
        declaredMethods: DeclaredMethods,
        callees:         Callees
    ): InterproceduralInterpretationHandler = new InterproceduralInterpretationHandler(
        cfg, ps, declaredMethods, callees
    )

}