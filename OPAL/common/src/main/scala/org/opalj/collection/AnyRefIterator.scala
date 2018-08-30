/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package collection

import scala.reflect.ClassTag
import scala.collection.AbstractIterator
import scala.collection.GenTraversableOnce

/**
 * Iterator over a collection of any ref values (AnyRefIterator[Char] would be an iterator over
 * the wrapper type).
 *
 * @note The type bound `T <: AnyRef` is expected to be ex-/implicitly enforced by subclasses.
 *
 * @author Michael Eichberg
 */
abstract class AnyRefIterator[+T] extends AbstractIterator[T] { self ⇒

    def ++[X >: T <: AnyRef](other: GenTraversableOnce[X]): AnyRefIterator[X] = {
        val that = other.toIterator
        new AnyRefIterator[X] {
            def hasNext: Boolean = self.hasNext || that.hasNext
            def next(): X = if (self.hasNext) self.next() else that.next()
        }
    }

    def ++[X >: T <: AnyRef](that: AnyRefIterator[X]): AnyRefIterator[X] = new AnyRefIterator[X] {
        def hasNext: Boolean = self.hasNext || that.hasNext
        def next(): X = if (self.hasNext) self.next() else that.next()
    }

    def sum(f: T ⇒ Int): Int = {
        var sum = 0
        while (hasNext) sum += f(next())
        sum
    }

    def collect(pf: PartialFunction[T, Int]): IntIterator = {
        ???
        /*
        def collect[B <: AnyRef](f: PartialFunction[Instruction, B]): List[PCAndAnyRef[B]] = {
            val max_pc = instructions.length
            var pc = 0
            var result: List[PCAndAnyRef[B]] = List.empty
            while (pc < max_pc) {
                val instruction = instructions(pc)
                val r: Any = f.applyOrElse(instruction, AnyToAnyThis)
                if (r.asInstanceOf[AnyRef] ne AnyToAnyThis) {
                    result ::= PCAndAnyRef(pc, r.asInstanceOf[B])
                }
                pc = pcOfNextInstruction(pc)
            }
            result.reverse
        }
        */
    }

    def collect(pf: PartialFunction[T, Long]): LongIterator = {
        ???
    }

    override def drop(n: Int): this.type = {
        var i = 0
        while (i < n && hasNext) { i += 1; next() }
        this
    }

    override def filter(p: T ⇒ Boolean): AnyRefIterator[T] = new AnyRefIterator[T] {
        private[this] var hasNextValue: Boolean = true
        private[this] var v: T = _
        private[this] def goToNextValue(): Unit = {
            while (self.hasNext) {
                v = self.next()
                if (p(v)) return ;
            }
            hasNextValue = false
        }

        goToNextValue()

        def hasNext: Boolean = hasNextValue
        def next(): T = { val v = this.v; goToNextValue(); v }
    }

    override def filterNot(p: T ⇒ Boolean): AnyRefIterator[T] = filter(e ⇒ !p(e))

    // TODO Introduce AnyRefCollection!
    // def flatMap[X](f: T ⇒ AnyRefCollection[X]): AnyRefIterator[X] = {
    //    ???
    // }

    def flatMap[X](f: T ⇒ AnyRefIterator[X]): AnyRefIterator[X] = {
        new AnyRefIterator[X] {
            private[this] var it: Iterator[X] = Iterator.empty
            private[this] def advanceIterator(): Unit = {
                while (!it.hasNext) {
                    if (self.hasNext) {
                        it = f(self.next()).toIterator
                    } else {
                        it = null
                        return ;
                    }
                }
            }
            advanceIterator()
            def hasNext: Boolean = it != null
            def next(): X = { val e = it.next(); advanceIterator(); e }
        }
    }

    def flatMap(f: T ⇒ IntIterator): IntIterator = new IntIterator {
        private[this] var it: IntIterator = IntIterator.empty
        private[this] def advanceIterator(): Unit = {
            while (!it.hasNext) {
                if (self.hasNext) {
                    it = f(self.next())
                } else {
                    it = null
                    return ;
                }
            }
        }
        advanceIterator()
        def hasNext: Boolean = it != null
        def next(): Int = { val e = it.next(); advanceIterator(); e }
    }

    def flatMap(f: T ⇒ LongIterator): LongIterator = new LongIterator {
        private[this] var it: LongIterator = LongIterator.empty
        private[this] def advanceIterator(): Unit = {
            while (!it.hasNext) {
                if (self.hasNext) {
                    it = f(self.next())
                } else {
                    it = null
                    return ;
                }
            }
        }
        advanceIterator()
        def hasNext: Boolean = it != null
        def next(): Long = { val e = it.next(); advanceIterator(); e }
    }

    def foldLeft(z: Int)(op: (Int, T) ⇒ Int): Int = {
        var v = z
        while (hasNext) v = op(v, next())
        v
    }

    def foldLeft(z: Long)(op: (Long, T) ⇒ Long): Long = {
        var v = z
        while (hasNext) v = op(v, next())
        v
    }

    override def map[X](f: T ⇒ X): AnyRefIterator[X] = new AnyRefIterator[X] {
        def hasNext: Boolean = self.hasNext
        def next(): X = f(self.next())
    }

    def map(f: T ⇒ Int): IntIterator = new IntIterator {
        def hasNext: Boolean = self.hasNext
        def next(): Int = f(self.next())
    }

    def map(f: T ⇒ Long): LongIterator = new LongIterator {
        def hasNext: Boolean = self.hasNext
        def next(): Long = f(self.next())
    }

    override def withFilter(p: T ⇒ Boolean): AnyRefIterator[T] = filter(p)

    def zip[X <: AnyRef](that: AnyRefIterator[X]): AnyRefIterator[(T, X)] = new AnyRefIterator[(T, X)] {
        def hasNext: Boolean = self.hasNext && that.hasNext
        def next: (T, X) = (self.next(), that.next())
    }

    override def zipWithIndex: AnyRefIterator[(T, Int)] = new AnyRefIterator[(T, Int)] {
        private[this] var idx = 0
        def hasNext: Boolean = self.hasNext
        def next: (T, Int) = { val ret = (self.next(), idx); idx += 1; ret }
    }

}

object AnyRefIterator {

    final val empty: AnyRefIterator[Nothing] = new AnyRefIterator[Nothing] {
        def hasNext: Boolean = false
        def next(): Nothing = throw new UnsupportedOperationException
    }

    def apply[T <: AnyRef](v: T): AnyRefIterator[T] = new AnyRefIterator[T] {
        private[this] var returned = false
        def hasNext: Boolean = !returned
        def next(): T = { returned = true; v }
    }

    def apply[T <: AnyRef](v1: T, v2: T): AnyRefIterator[T] = new AnyRefIterator[T] {
        private[this] var nextId = 0
        def hasNext: Boolean = nextId < 2
        def next(): T = { if (nextId == 0) { nextId = 1; v1 } else { nextId = 2; v2 } }
        override def toArray[X >: T: ClassTag]: Array[X] = Array[X](v1, v2)
    }

    def apply[T <: AnyRef](v1: T, v2: T, v3: T): AnyRefIterator[T] = new AnyRefIterator[T] {
        private[this] var nextId: Int = 0
        def hasNext: Boolean = nextId < 3
        def next(): T = { nextId += 1; if (nextId == 1) v1 else if (nextId == 2) v2 else v3 }
        override def toArray[X >: T: ClassTag]: Array[X] = Array[X](v1, v2, v3)
    }

    def fromNonNullValues[T <: AnyRef](data: Array[T]): AnyRefIterator[T] = new AnyRefIterator[T] {
        private[this] var i = -1
        private[this] def advanceIterator(): Unit = {
            i += 1
            val max = data.length
            while (i < max && data(i) == null) i += 1
        }
        advanceIterator()
        def hasNext: Boolean = i < data.length
        def next(): T = { val v = data(i); advanceIterator(); v }
    }

}
