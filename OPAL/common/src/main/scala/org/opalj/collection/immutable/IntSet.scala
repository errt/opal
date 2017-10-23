/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package collection
package immutable

import scala.collection.mutable.Builder

/**
 * A set of integer values.
 *
 * @author Michael Eichberg
 */
trait IntSet[T <: IntSet[T]] { this: T ⇒

    def isEmpty: Boolean
    def nonEmpty: Boolean = !isEmpty
    def isSingletonSet: Boolean
    def hasMultipleElements: Boolean
    def size: Int

    def foreach[U](f: Int ⇒ U): Unit
    def withFilter(p: (Int) ⇒ Boolean): T
    def map(f: Int ⇒ Int): T
    def flatMap(f: Int ⇒ T): T

    def foldLeft[B](z: B)(f: (B, Int) ⇒ B): B

    def head: Int
    def contains(value: Int): Boolean
    def exists(p: Int ⇒ Boolean): Boolean
    def forall(f: Int ⇒ Boolean): Boolean

    def -(i: Int): T
    def +(i: Int): T

    final def --(is: Traversable[Int]): T = {
        var r = this
        is.foreach { i ⇒ r -= i }
        r
    }
    final def --(is: IntSet[_]): T = {
        var r = this
        is.foreach { i ⇒ r -= i }
        r
    }

    final def ++(that: T): T = {
        if (this.size > that.size)
            that.foldLeft(this)(_ + _) // we expand `this` since `this` is larger
        else
            this.foldLeft(that)(_ + _) // we expand `that`
    }

    def iterator: Iterator[Int]
    final def transform[B, To](f: Int ⇒ B, b: Builder[B, To]): To = {
        foreach(i ⇒ b += f(i))
        b.result()
    }
    def toIntIterator: IntIterator
    def toChain: Chain[Int]

    final def mkString(pre: String, in: String, post: String): String = {
        val sb = new StringBuilder(pre)
        val it = toIntIterator
        var hasNext = it.hasNext
        while (hasNext) {
            sb.append(it.next.toString)
            hasNext = it.hasNext
            if (hasNext) sb.append(in)
        }
        sb.append(post)
        sb.toString()
    }

    final def mkString(in: String): String = mkString("", in, "")

}
