/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
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
package fpa

import org.opalj.fp.Property
import org.opalj.fp.PropertyKey
import org.opalj.br.Method
import org.opalj.fp.PropertyStore
import org.opalj.fp.PropertyComputationResult
import org.opalj.AnalysisModes
import org.opalj.fp.Entity
import org.opalj.fp.ImmediateResult
import org.opalj.br.analyses.SomeProject
import org.opalj.fp.ImmediateMultiResult

/**
 * @author Michael Reif
 */

sealed trait ProjectAccessibility extends Property {
    final def key = ProjectAccessibility.Key
}

object ProjectAccessibility {
    final val Key = PropertyKey.create("Accessible", Global)
}

case object Global extends ProjectAccessibility { final val isRefineable = false }

case object PackageLocal extends ProjectAccessibility { final val isRefineable = false }

case object ClassLocal extends ProjectAccessibility { final val isRefineable = false }

object StaticMethodAccessibilityAnalysis
        extends AssumptionBasedFixpointAnalysis
        with FilterEntities[Method] {

    val propertyKey = ProjectAccessibility.Key

    /**
     * Determines the [[ProjectAccessibility]] property of static methods considering shadowing of methods
     * provided by super classes. It is tailored to entry point set computation where we have to consider different kind
     * of assumption depending on the analyzed program.
     *
     * Computational differences regarding static methods are :
     *  - private methods can be handled equal in every context
     *  - if OPA is met, all package visible classes are visible which implies that all non-private methods are
     *    visible too
     *  - if CPA is met, methods in package visible classes are not visible by default.
     *
     */
    def determineProperty(
        method: Method)(
            implicit project: SomeProject,
            store: PropertyStore): PropertyComputationResult = {

        if (method.isPrivate)
            return ImmediateResult(method, ClassLocal)

        if (isOpenLibrary)
            return ImmediateResult(method, Global)

        val declClass = project.classFile(method)
        val pgkVisibleMethod = method.isPackagePrivate
        
        if (pgkVisibleMethod)
            return ImmediateResult(method, PackageLocal)

        if (declClass.isPublic && 
                (method.isPublic || 
                        (!declClass.isFinal && method.isProtected)))
            return ImmediateResult(method, Global)

        val declaringClassType = declClass.thisType

        val methodDescriptor = method.descriptor
        val methodName = method.name

        var subtypes = project.classHierarchy.directSubtypesOf(declaringClassType)
        while (subtypes.nonEmpty) {
            val curSubtype = subtypes.head
            val classFileO = project.classFile(curSubtype)
            if (classFileO.isDefined) {
                val classFile = classFileO.get
                val declMethod = classFile.findMethod(methodName, methodDescriptor)
                if (declMethod.isEmpty) {
                    if (classFile.isPublic)
                        return ImmediateResult(method, Global)
                    else
                        subtypes ++= project.classHierarchy.directSubtypesOf(curSubtype)
                }
            } else return ImmediateResult(method, Global)

            subtypes -= curSubtype
        }

        // If no subtype is found, the method is not accessible
        ImmediateResult(method, PackageLocal)
    }

    val entitySelector: PartialFunction[Entity, Method] = {
        case m: Method if m.isStatic && !m.isStaticInitializer ⇒ m
    }
}