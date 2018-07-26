/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package properties
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.SomeProject
import org.opalj.collection.immutable.LongTrieSet

/**
 * For a given [[DeclaredMethod]], and for each call site (represented by the PC), the set of methods
 * that are possible call targets.
 *
 * @author Florian Kuebler
 */
sealed trait CallersPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = CallersProperty
}

sealed trait CallersProperty extends Property with OrderedProperty with CallersPropertyMetaInformation {

    def hasCallersWithUnknownContext: Boolean

    def hasVMLevelCallers: Boolean

    def size: Int

    def callers(implicit declaredMethods: DeclaredMethods): TraversableOnce[(DeclaredMethod, Int /*PC*/ )] //TODO: maybe use traversable instead of set

    def updated(caller: DeclaredMethod, pc: Int)(implicit declaredMethods: DeclaredMethods): CallersProperty

    def updateWithUnknownContext(): CallersProperty

    def updateVMLevelCall(): CallersProperty

    override def toString: String = {
        s"Callers(size=${this.size})"
    }

    final def key: PropertyKey[CallersProperty] = CallersProperty.key

    /**
     * Tests if this property is equal or better than the given one (better means that the
     * value is above the given value in the underlying lattice.)
     */
    override def checkIsEqualOrBetterThan(e: Entity, other: CallersProperty): Unit = {
        if (other.size < size) //todo if (!pcMethodPairs.subsetOf(other.pcMethodPairs))
            throw new IllegalArgumentException(s"$e: illegal refinement of property $other to $this")
    }
}

trait CallersWithoutUnknownContext extends CallersProperty {
    override def hasCallersWithUnknownContext: Boolean = false
}

trait CallersWithUnknownContext extends CallersProperty {
    override def hasCallersWithUnknownContext: Boolean = true
    override def updateWithUnknownContext(): CallersWithUnknownContext = this
}

trait CallersWithVMLevelCall extends CallersProperty {
    override def hasVMLevelCallers: Boolean = true
    override def updateVMLevelCall(): CallersWithVMLevelCall = this
}

trait CallersWithoutVMLevelCall extends CallersProperty {
    override def hasVMLevelCallers: Boolean = false
}

trait EmptyConcreteCallers extends CallersProperty {
    override def size: Int = 0

    override def callers(implicit declaredMethods: DeclaredMethods): TraversableOnce[(DeclaredMethod, Int)] = {
        Nil
    }

    override def updated(caller: DeclaredMethod, pc: Int)(implicit declaredMethods: DeclaredMethods): CallersProperty = {
        val set = LongTrieSet(CallersProperty.toLong(caller.id, pc))

        if (!hasCallersWithUnknownContext && !hasVMLevelCallers) {
            new CallersOnlyWithConcreteCallers(set)
        } else {
            CallersImplWithOtherCalls(set, hasVMLevelCallers, hasCallersWithUnknownContext)
        }
    }
}

object NoCallers extends EmptyConcreteCallers with CallersWithoutUnknownContext with CallersWithoutVMLevelCall {
    override def updateVMLevelCall(): CallersWithVMLevelCall = OnlyVMLevelCallers

    override def updateWithUnknownContext(): CallersWithUnknownContext = OnlyCallersWithUnknownContext
}

object OnlyCallersWithUnknownContext
    extends EmptyConcreteCallers with CallersWithUnknownContext with CallersWithoutVMLevelCall {
    override def updateVMLevelCall(): CallersWithVMLevelCall = OnlyVMCallersAndWithUnknownContext
}

object OnlyVMLevelCallers
    extends EmptyConcreteCallers with CallersWithoutUnknownContext with CallersWithVMLevelCall {
    override def updateWithUnknownContext(): CallersWithUnknownContext = OnlyVMCallersAndWithUnknownContext
}

object OnlyVMCallersAndWithUnknownContext
    extends EmptyConcreteCallers with CallersWithVMLevelCall with CallersWithUnknownContext

trait CallersImplementation extends CallersProperty {
    val encodedCallers: LongTrieSet /* MethodId + PC*/
    override def size: Int = encodedCallers.size

    override def callers(
        implicit
        declaredMethods: DeclaredMethods
    ): TraversableOnce[(DeclaredMethod, Int /*PC*/ )] = {
        for {
            encodedPair ← encodedCallers.iterator
            (mId, pc) = CallersProperty.toMethodAndPc(encodedPair)
        } yield declaredMethods(mId) → pc
    }
}

class CallersOnlyWithConcreteCallers(
        val encodedCallers: LongTrieSet /*MethodId + PC*/
) extends CallersImplementation with CallersWithoutVMLevelCall with CallersWithoutUnknownContext {

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = {
        val encodedCaller = CallersProperty.toLong(caller.id, pc)
        if (encodedCallers.contains(encodedCaller))
            this
        else
            new CallersOnlyWithConcreteCallers(encodedCallers + encodedCaller)
    }

    override def updateWithUnknownContext(): CallersProperty =
        CallersImplWithOtherCalls(
            encodedCallers,
            hasVMLevelCallers = false,
            hasCallersWithUnknownContext = true
        )

    override def updateVMLevelCall(): CallersProperty =
        CallersImplWithOtherCalls(
            encodedCallers,
            hasVMLevelCallers = true,
            hasCallersWithUnknownContext = false
        )
}

class CallersImplWithOtherCalls(
        val encodedCallers: LongTrieSet /*MethodId + PC*/ ,
        val coding:         Byte // last bit vm lvl, second last bit unknown context
) extends CallersImplementation {
    assert(!encodedCallers.isEmpty)
    assert(coding >= 0 && coding <= 3)

    override def hasVMLevelCallers: Boolean = (coding & 1) != 0

    override def hasCallersWithUnknownContext: Boolean = (coding & 2) != 0

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = {
        val encodedCaller = CallersProperty.toLong(caller.id, pc)
        if (encodedCallers.contains(encodedCaller: java.lang.Long))
            this
        else
            new CallersImplWithOtherCalls(encodedCallers + encodedCaller, coding)
    }

    override def updateVMLevelCall(): CallersProperty =
        if (hasVMLevelCallers)
            this
        else
            new CallersImplWithOtherCalls(encodedCallers, (coding | 1).toByte)

    override def updateWithUnknownContext(): CallersProperty =
        if (hasCallersWithUnknownContext)
            this
        else
            new CallersImplWithOtherCalls(encodedCallers, (coding | 2).toByte)
}

object CallersImplWithOtherCalls {
    def apply(
        encodedCallers:               LongTrieSet /* MethodId + PC */ ,
        hasVMLevelCallers:            Boolean,
        hasCallersWithUnknownContext: Boolean
    ): CallersImplWithOtherCalls = {
        assert(hasVMLevelCallers | hasCallersWithUnknownContext)
        assert(!encodedCallers.isEmpty)

        val vmLvlCallers = if (hasVMLevelCallers) 1 else 0
        val unknownContext = if (hasCallersWithUnknownContext) 2 else 0

        new CallersImplWithOtherCalls(
            encodedCallers, (vmLvlCallers | unknownContext).toByte
        )
    }
}

class LowerBoundCallers(
        project: SomeProject, method: DeclaredMethod
) extends CallersWithUnknownContext with CallersWithVMLevelCall {

    override lazy val size: Int = {
        //callees.size * project.allMethods.size
        // todo this is for performance improvement only
        Int.MaxValue
    }

    override def callers(
        implicit
        declaredMethods: DeclaredMethods
    ): TraversableOnce[(DeclaredMethod, Int /*PC*/ )] = {
        ??? // todo
    }

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = this
}

object CallersProperty extends CallersPropertyMetaInformation {

    final val key: PropertyKey[CallersProperty] = {
        PropertyKey.create(
            "CallersProperty",
            (ps: PropertyStore, reason: FallbackReason, m: DeclaredMethod) ⇒ reason match {
                case PropertyIsNotComputedByAnyAnalysis ⇒
                    CallersProperty.fallback(m, ps.context(classOf[SomeProject]))
                case PropertyIsNotDerivedByPreviouslyExecutedAnalysis ⇒
                    NoCallers
            },
            (_: PropertyStore, eps: EPS[DeclaredMethod, CallersProperty]) ⇒ eps.ub,
            (_: PropertyStore, _: DeclaredMethod) ⇒ None
        )
    }

    def fallback(m: DeclaredMethod, p: SomeProject): CallersProperty = {
        new LowerBoundCallers(p, m)
    }

    def toLong(methodId: Int, pc: Int): Long = {
        assert(pc >= 0 && pc < 0xFFFFL)
        (methodId.toLong << 16) | pc
    }
    def toMethodAndPc(methodAndPc: Long): (Int, Int) = {
        ((methodAndPc >> 16).toInt, methodAndPc.toInt & 0xFFFF)
    }
}
