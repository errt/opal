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
package de.tud.cs.st.bat
package resolved
package ai

import reader.Java6Framework
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Spec
import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers

/**
  * Basic tests of the abstract interpreter.
  *
  * @author Michael Eichberg
  */
@RunWith(classOf[JUnitRunner])
class AI_SimpleMethodsTest extends FlatSpec with ShouldMatchers /*with BeforeAndAfterAll */ {

   class RecordingDomain extends TypeDomain {
      var returnedValue : Option[Value] = _
      override def areturn(value : Value) { returnedValue = Some(value) }
      override def dreturn(value : Value) { returnedValue = Some(value) }
      override def freturn(value : Value) { returnedValue = Some(value) }
      override def ireturn(value : Value) { returnedValue = Some(value) }
      override def lreturn(value : Value) { returnedValue = Some(value) }
      override def returnVoid() { returnedValue = None }
   }

   val classFile = Java6Framework.ClassFiles("test/classfiles/ai.zip").find(_.thisClass.className == "ai/SimpleMethods").get

   behavior of "the basic abstract interpreter"

   it should "be able to analyze a method that does nothing" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "nop").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(None)
   }

   it should "be able to analyze a method that returns a fixed value" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "one").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeIntegerValue))
   }

   it should "be able to analyze a method that just returns a given value" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "identity").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeIntegerValue))
   }

   it should "be able to analyze that adds two values" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "add").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeIntegerValue))
   }

   it should "be able to analyze a method that casts an int to a byte" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "toByte").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeByteValue))
   }

   it should "be able to analyze a method that casts an int to a short" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "toShort").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeShortValue))
   }

   it should "be able to analyze a method that multiplies a value by two" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "twice").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeDoubleValue))
   }

   it should "be able to handle simple methods correctly" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "objectToString").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(TypedValue.AString))
   }

   it should "be able to correctly handle casts" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "asSimpleMethods").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(TypedValue(ObjectType("ai/SimpleMethods"))))
   }

   it should "be able to analyze a method that squares a given double value" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "square").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeDoubleValue))
   }

   it should "be able to analyze a classical setter method" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "setValue").get
      val result = AI(classFile, method)(domain)

      result should not be (null)
      domain.returnedValue should be(None)
   }

   it should "be able to analyze a classical getter method" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "getValue").get
      /*val result =*/ AI(classFile, method)(domain)

      domain.returnedValue should be(Some(SomeFloatValue))
   }

   it should "be able to return the correct type of an object if an object that is passed in is directly returned" in {
      implicit val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "asIs").get
      val t = ObjectType("some/Foo")
      val locals = new Array[Value](1)
      locals(0) = TypedValue(t)
      AI(method.body.get.instructions, locals)

      domain.returnedValue should be(Some(TypedValue(t)))
   }

   it should "be able to analyze a method that creates an instance of an object using reflection" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "create").get
      val result = AI(classFile, method)(domain)

      domain.returnedValue should be(Some(TypedValue(ObjectType.Object)))
   }

   it should "be able to analyze a method that creates an object and which calls multiple methods of the new object" in {
      val domain = new RecordingDomain
      val method = classFile.methods.find(_.name == "multipleCalls").get
      val result = AI(classFile, method)(domain)

      domain.returnedValue should be(None)
   }
}
