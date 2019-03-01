/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac

import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.opalj.util.PerformanceEvaluation.time
import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import org.opalj.br.TestSupport
import org.opalj.ai.Domain
import org.opalj.ai.BaseAI
import org.opalj.ai.domain.RecordDefUse
import org.opalj.ai.domain.l0.PrimitiveTACAIDomain
import org.opalj.ai.domain.l1.DefaultDomainWithCFGAndDefUse
import org.opalj.ai.domain.l2.DefaultPerformInvocationsDomainWithCFGAndDefUse

/**
 * Tests that all methods of OPAL's test projects + the JDK can be converted to the ai-based
 * three address representation.
 *
 * @author Michael Eichberg
 * @author Roberts Kolosovs
 */
@RunWith(classOf[JUnitRunner])
class TACAIIntegrationTest extends FunSpec with Matchers {

    def checkProject(
        project:       SomeProject,
        domainFactory: (SomeProject, Method) ⇒ Domain with RecordDefUse
    ): Unit = {
        if (Thread.currentThread().isInterrupted) return ;

        var errors: List[(String, Throwable)] = Nil
        val successfullyCompleted = new java.util.concurrent.atomic.AtomicInteger(0)
        val ch = project.classHierarchy
        for {
            cf ← project.allProjectClassFiles.par
            if !Thread.currentThread().isInterrupted
            m ← cf.methods
            body ← m.body
        } {
            val aiResult = BaseAI(m, domainFactory(project, m))
            try {

                val TACode(params, tacAICode, _, cfg, _, _) = TACAI(m, ch, aiResult)(List.empty)
                ToTxt(params, tacAICode, cfg, false, true, true)

                // Some additional consistency tests...

                tacAICode.iterator.zipWithIndex foreach { stmtIndex ⇒
                    val (stmt, index) = stmtIndex
                    val bb = cfg.bb(index)
                    if (bb.endPC == index && bb.mayThrowException && stmt.isSideEffectFree) {
                        fail(
                            s"$stmt is side effect free "+
                                "but the basic block has a catch node/abnormal exit node as a successor"
                        )
                    }
                }

                successfullyCompleted.incrementAndGet()
            } catch {
                case e: Throwable ⇒ this.synchronized {
                    val methodSignature = m.toJava

                    println(methodSignature+" - size: "+body.instructions.length)
                    e.printStackTrace(Console.out)
                    if (e.getCause != null) {
                        println("\tcause:")
                        e.getCause.printStackTrace(Console.out)
                    }
                    val instrWithIndex = body.instructions.zipWithIndex.filter(_._1 != null)
                    println(
                        instrWithIndex.map(_.swap).mkString("Instructions:\n\t", "\n\t", "\n")
                    )
                    println(
                        body.exceptionHandlers.mkString("Exception Handlers:\n\t", "\n\t", "\n")
                    )
                    errors ::= ((project.source(cf)+":"+methodSignature, e))
                }
            }
        }
        if (errors.nonEmpty) {
            val summary =
                s"successfully transformed ${successfullyCompleted.get} methods: "+
                    "; failed methods: "+errors.size+"\n"
            val message = errors.map(_.toString()).mkString("Errors thrown:\n", "\n\n", summary)
            fail(message)
        }
    }

    protected def domainFactories = {
        Seq[(String, (SomeProject, Method) ⇒ Domain with RecordDefUse)](
            (
                "l0.PrimitiveTACAIDomain",
                (p: SomeProject, m: Method) ⇒ new PrimitiveTACAIDomain(p.classHierarchy, m)
            ),
            (
                "l1.DefaultDomainWithCFGAndDefUse",
                (p: SomeProject, m: Method) ⇒ new DefaultDomainWithCFGAndDefUse(p, m)
            ),
            (
                "l2.DefaultPerformInvocationsDomainWithCFGAndDefUse",
                (p: SomeProject, m: Method) ⇒ {
                    new DefaultPerformInvocationsDomainWithCFGAndDefUse(p, m)
                }
            )
        )
    }

    // TESTS

    describe(s"creating the 3-address code") {

        TestSupport.allBIProjects() foreach { biProject ⇒
            val (name, projectFactory) = biProject
            it(s"for $name") {
                var p = projectFactory()
                domainFactories foreach { domainInformation ⇒
                    val (domainName, domainFactory) = domainInformation
                    time {
                        checkProject(p, domainFactory)
                    } { t ⇒ info(s"using $domainName the conversion took ${t.toSeconds}") }
                    p = p.recreate()
                }
            }
        }

        it(s"for the (current) JDK") {
            var p = TestSupport.createJREProject()
            domainFactories foreach { domainInformation ⇒
                val (domainName, domainFactory) = domainInformation
                time {
                    checkProject(p, domainFactory)
                } { t ⇒ info(s"using $domainName the conversion took ${t.toSeconds}") }
                p = p.recreate()
            }
        }

    }
}