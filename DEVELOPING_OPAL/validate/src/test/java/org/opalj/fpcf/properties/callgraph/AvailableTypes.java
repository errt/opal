/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.properties.callgraph;

import org.opalj.fpcf.properties.PropertyValidator;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Describes which types are known to be instantiated and available in the annotated
 * entity (class, field or method).
 *
 * These facts are to be determined by a dataflow-based call graph algorithm (e.g., XTA).
 *
 * @author Andreas Bauer
 */
@Retention(CLASS)
@Target({FIELD, METHOD, CONSTRUCTOR, TYPE})
@Documented
@PropertyValidator(key = "AvailableTypes", validator = AvailableTypesMatcher.class)
public @interface AvailableTypes {

    /**
     * Set of available types.
     *
     * Must be given in JVM binary notation (e.g. Ljava/lang/Object;)
     *
     * The set given should match the computed types exactly (i.e., it is not a minimum
     * expectation).
     */
    String[] value() default {};
}
