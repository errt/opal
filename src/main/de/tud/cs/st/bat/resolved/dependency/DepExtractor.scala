/* License (BSD Style License):
*  Copyright (c) 2009, 2011
*  Software Technology Group
*  Department of Computer Science
*  Technische Universität Darmstadt
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*
*  - Redistributions of source code must retain the above copyright notice,
*    this list of conditions and the following disclaimer.
*  - Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*  - Neither the name of the Software Technology Group or Technische 
*    Universität Darmstadt nor the names of its contributors may be used to 
*    endorse or promote products derived from this software without specific 
*    prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
*  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
*  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
*  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
*  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
*  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
*  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
*  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
*  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*/
package de.tud.cs.st.bat.resolved
package dependency

import java.lang.Integer
import DependencyType._
import de.tud.cs.st.bat.canonical.AccessFlagsContext._
import de.tud.cs.st.bat.canonical.ACC_STATIC
import de.tud.cs.st.bat.canonical.AccessFlagsIterator

/**
 * DepExtractor can process a ClassFile and extract all dependencies between
 * class, interfaces, fields and methods. The dependencies are passed to
 * the addDep-method provided by the given DepBuilder.
 *
 * @author Thomas Schlosser
 */
class DepExtractor(val builder: DepBuilder) extends InstructionDepExtractor {

  val FIELD_AND_METHOD_SEPARATOR = "."

  def process(clazz: ClassFile) {
    implicit val thisClassName = getName(clazz.thisClass)
    implicit val thisClassID = getID(clazz.thisClass)

    // process super class
    val superClassID = getID(clazz.superClass)
    builder.addDep(thisClassID, superClassID, EXTENDS)

    // process interfaces
    for (interface <- clazz.interfaces) {
      val interfaceID = getID(interface)
      builder.addDep(thisClassID, interfaceID, IMPLEMENTS)
    }

    //process attributes
    for (attribute <- clazz.attributes) {
      attribute match {
        case aa: Annotations_Attribute =>
          for (annotation <- aa.annotations) {
            process(annotation)
          }
        case ema: EnclosingMethod_attribute => {
          if (isEnclosedByMethod(ema)) {
            builder.addDep(thisClassID, getID(ema.clazz, ema.name, ema.descriptor), IS_INNER_CLASS_OF)
          } else {
            builder.addDep(thisClassID, getID(ema.clazz), IS_INNER_CLASS_OF)
          }
        }
        case ica: InnerClasses_attribute =>
          for (c <- ica.classes) {
            if (c.outerClassType != null && c.outerClassType.eq(clazz.thisClass)) {
              builder.addDep(getID(c.innerClassType), thisClassID, IS_INNER_CLASS_OF)
            }
          }
        //TODO: make use of Signature_attribute
        case sa: Signature_attribute =>
          println(sa)
        case _ => Nil
      }
    }

    //process fields
    for (field <- clazz.fields) {
      process(field)
    }

    //process methods
    for (method <- clazz.methods) {
      process(method)
    }
  }

  private def process(field: Field_Info)(implicit thisClassName: String, thisClassID: Option[Int]) {
    implicit val fieldID = getID(thisClassName, field)
    builder.addDep(fieldID, thisClassID, if(isStaticField(field.accessFlags)) IS_CLASS_MEMBER_OF else IS_INSTANCE_MEMBER_OF)
    builder.addDep(fieldID, getID(field.descriptor.fieldType), IS_OF_TYPE)
    //process attributes
    for (attribute <- field.attributes) {
      attribute match {
        case aa: Annotations_Attribute =>
          for (annotation <- aa.annotations) {
            process(annotation)(fieldID)
          }
        case cva: ConstantValue_attribute =>
          builder.addDep(fieldID, getID(cva.constantValue.valueType), USES_CONSTANT_VALUE_OF_TYPE)
        //TODO: make use of Signature_attribute
        case sa: Signature_attribute =>
          println(sa)
        case _ => Nil
      }
    }
  }

  private def process(method: Method_Info)(implicit thisClassName: String, thisClassID: Option[Int]) {
    implicit val methodID = getID(thisClassName, method)
    builder.addDep(methodID, thisClassID, if(isStaticMethod(method.accessFlags)) IS_CLASS_MEMBER_OF else IS_INSTANCE_MEMBER_OF)
    builder.addDep(methodID, getID(method.descriptor.returnType), RETURNS)
    for (paramType <- method.descriptor.parameterTypes) {
      builder.addDep(methodID, getID(paramType), HAS_PARAMETER_OF_TYPE)
    }

    //process attributes
    for (attribute <- method.attributes) {
      attribute match {
        case aa: Annotations_Attribute =>
          for (annotation <- aa.annotations) {
            process(annotation)(methodID)
          }
        case paa: ParameterAnnotations_attribute =>
          for (annotations <- paa.parameterAnnotations) {
            for (annotation <- annotations) {
              process(annotation)(methodID, PARAMETER_ANNOTATED_WITH)
            }
          }
        case ea: Exceptions_attribute =>
          for (exception <- ea.exceptionTable) {
            builder.addDep(methodID, getID(exception), THROWS)
          }
        case ada: AnnotationDefault_attribute => {
          processElementValue(ada.elementValue)(methodID)
        }
        case ca: Code_attribute =>
          process(methodID, ca.code)
          for (exception <- ca.exceptionTable) {
            if (!isFinallyBlock(exception.catchType)) {
              builder.addDep(methodID, getID(exception.catchType), CATCHES)
            }
          }
          for (attr <- ca.attributes) {
            attr match {
              case lvta: LocalVariableTable_attribute =>
                for (lvte <- lvta.localVariableTable) {
                  builder.addDep(methodID, getID(lvte.fieldType), HAS_LOCAL_VARIABLE_OF_TYPE)
                }
              //TODO: make use of LocalVariableTypeTable_attribute
              case lvtta: LocalVariableTypeTable_attribute =>
                println(lvtta)
              case _ => Nil
            }
          }
        //TODO: make use of Signature_attribute
        case sa: Signature_attribute =>
          println(sa)
        case _ => Nil
      }
    }
  }

  private def processElementValue(elementValue: ElementValue)(implicit srcID: Option[Int], annotationDepType: DependencyType = ANNOTATED_WITH) {
    elementValue match {
      case ClassValue(returnType) =>
        builder.addDep(srcID, getID(returnType), USES_DEFAULT_CLASS_VALUE_TYPE)
      case EnumValue(enumType, constName) =>
        builder.addDep(srcID, getID(enumType), USES_DEFAULT_ENUM_VALUE_TYPE)
        builder.addDep(srcID, getID(enumType, constName), USES_ENUM_VALUE)
      case ArrayValue(values) =>
        for (value <- values) {
          processElementValue(value)
        }
      case AnnotationValue(annotation) =>
        process(annotation)(srcID, USES_DEFAULT_ANNOTATION_VALUE_TYPE)
      case _ => Nil
    }
  }

  private def process(annotation: Annotation)(implicit srcID: Option[Int], depType: DependencyType = ANNOTATED_WITH) {
    builder.addDep(srcID, getID(annotation.annotationType), depType)
    for (elemValuePair <- annotation.elementValuePairs) {
      processElementValue(elemValuePair.elementValue)
    }
  }

  private def isFinallyBlock(catchType: ObjectType): Boolean =
    catchType == null

  private def isEnclosedByMethod(ema: EnclosingMethod_attribute): Boolean =
    ema.name != null && ema.descriptor != null // otherwise the inner class is assigned to a field
    
  private def isStaticField(accessFlags: Int): Boolean =
    AccessFlagsIterator(accessFlags,FIELD).contains(ACC_STATIC)
  private def isStaticMethod(accessFlags: Int): Boolean =
    AccessFlagsIterator(accessFlags,METHOD).contains(ACC_STATIC)

  protected def filter(name: String): Boolean = {
    for (swFilter <- Array("byte", "short", "int", "long", "float", "double", "char", "boolean", "void")) {
      if (name.startsWith(swFilter)) {
        return true
      }
    }
    false
  }
}
