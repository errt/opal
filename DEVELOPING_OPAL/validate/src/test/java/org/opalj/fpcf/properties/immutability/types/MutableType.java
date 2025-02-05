/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.properties.immutability.types;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.opalj.br.fpcf.FPCFAnalysis;
import org.opalj.fpcf.properties.PropertyValidator;
import org.opalj.br.fpcf.analyses.immutability.TypeImmutabilityAnalysis;

/**
 * Annotation to state that the annotated type is mutable.
 *
 * @author Tobias Roth
 */
@PropertyValidator(key = "TypeImmutability", validator = MutableTypeMatcher.class)
@Documented
@Retention(RetentionPolicy.CLASS)
public @interface MutableType {

    /**
     * A short reasoning of this property.
     */
    String value();

    Class<? extends FPCFAnalysis>[] analyses() default {TypeImmutabilityAnalysis.class};
}
