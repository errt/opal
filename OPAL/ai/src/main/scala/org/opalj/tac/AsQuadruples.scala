/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package tac

import scala.collection.mutable.BitSet
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

import org.opalj.collection.mutable.Locals
import org.opalj.bytecode.BytecodeProcessingFailedException
import org.opalj.br._
import org.opalj.br.instructions._
import org.opalj.ai.AIResult

/**
 * Converts the bytecode of a method into a three address/quadruples representation.
 *
 * @author Michael Eichberg
 * @author Roberts Kolosovs
 */
object AsQuadruples {

    /**
     * Converts the bytecode of a method into a quadruples representation.
     *
     * @param method A method with a body. I.e., a non-native, non-abstract method.
     */
    def apply(method: Method, aiResult: Option[AIResult]): Array[Stmt] = {

        import BinaryArithmeticOperators._
        import UnaryArithmeticOperators._
        //        import RelationalOperators._

        val code = method.body.get
        import code.pcOfNextInstruction
        val instructions = code.instructions
        val codeSize = instructions.size
        val processed = new BitSet(codeSize)

        // In a few cases, such as swap and dup instructions, we have to
        // create multiple three-address instructions. However, in this case
        // we have to make sure that jump targets are still pointing to the right
        // instructions. Hence, we have to make sure that all statements created
        // for one instruction are mapped to the same pc.
        val statements = new Array[List[Stmt]](codeSize)

        // Calculating the basic block boundaries on demand makes it possible to
        // use some very simple pattern matchers afterwards to perform common
        // code beautifications/optimizations without performing "heavy weight" analyses.
        // TODO val basicBlockBoundaries = new BitSet(codeSize)

        processed(0) = true
        var worklist: List[(PC, Stack)] = List((0, Nil))
        for { exceptionHandler ← code.exceptionHandlers } (
            worklist ::= ((exceptionHandler.handlerPC, List(OperandVar.HandledException)))
        )

        while (worklist.nonEmpty) {
            val (pc, stack) = worklist.head
            val instruction = instructions(pc)
            val opcode = instruction.opcode
            worklist = worklist.tail

            def schedule(nextPC: PC, newStack: Stack): Unit = {
                if (!processed(nextPC)) {
                    processed.add(nextPC)
                    worklist ::= ((nextPC, newStack))
                }
            }

            def loadInstruction(sourceRegister: UShort, cTpe: ComputationalType): Unit = {
                val operandVar = OperandVar(cTpe, stack)
                val registerVar = RegisterVar(cTpe, sourceRegister)
                statements(pc) = List(Assignment(pc, operandVar, registerVar))
                val newStack = operandVar :: stack
                val nextPC = pcOfNextInstruction(pc)
                schedule(nextPC, newStack)
            }

            def storeInstruction(targetRegister: UShort, cTpe: ComputationalType): Unit = {
                val operandVar = stack.head
                val registerVar = RegisterVar(cTpe, targetRegister)
                statements(pc) = List(Assignment(pc, registerVar, operandVar))
                val newStack = stack.tail
                val nextPC = pcOfNextInstruction(pc)
                schedule(nextPC, newStack)
            }

            // Note:
            // The computational type of the Binary Expression is determined using the
            // first (left) value of the expression. This makes it possible to use
            // this function for bit shift operations where value1 and
            // value2 may have different computational types, but the resulting type
            // is always determined by the type of value1.
            def binaryArithmeticOperation(operator: BinaryArithmeticOperator): Unit = {
                val value2 :: value1 :: _ = stack
                val cTpe = value1.cTpe
                statements(pc) = List(
                    Assignment(pc, value1, BinaryExpr(pc, cTpe, operator, value1, value2))
                )
                schedule(pcOfNextInstruction(pc), stack.tail)
            }

            def prefixArithmeticOperation(operator: UnaryArithmeticOperator): Unit = {
                val value :: _ = stack
                val cTpe = value.cTpe
                statements(pc) = List(
                    Assignment(pc, value, PrefixExpr(pc, cTpe, operator, value))
                )
                schedule(pcOfNextInstruction(pc), stack)
            }

            def as[T <: Instruction](i: Instruction): T = i.asInstanceOf[T]

            (opcode: @scala.annotation.switch) match {
                case ALOAD_0.opcode ⇒ loadInstruction(0, ComputationalTypeReference)
                case ALOAD_1.opcode ⇒ loadInstruction(1, ComputationalTypeReference)
                case ALOAD_2.opcode ⇒ loadInstruction(2, ComputationalTypeReference)
                case ALOAD_3.opcode ⇒ loadInstruction(3, ComputationalTypeReference)
                case ALOAD.opcode ⇒
                    loadInstruction(as[ALOAD](instruction).lvIndex, ComputationalTypeReference)

                case ILOAD_0.opcode ⇒ loadInstruction(0, ComputationalTypeInt)
                case ILOAD_1.opcode ⇒ loadInstruction(1, ComputationalTypeInt)
                case ILOAD_2.opcode ⇒ loadInstruction(2, ComputationalTypeInt)
                case ILOAD_3.opcode ⇒ loadInstruction(3, ComputationalTypeInt)
                case ILOAD.opcode ⇒
                    loadInstruction(as[ILOAD](instruction).lvIndex, ComputationalTypeInt)

                case ISTORE_0.opcode ⇒ storeInstruction(0, ComputationalTypeInt)
                case ISTORE_1.opcode ⇒ storeInstruction(1, ComputationalTypeInt)
                case ISTORE_2.opcode ⇒ storeInstruction(2, ComputationalTypeInt)
                case ISTORE_3.opcode ⇒ storeInstruction(3, ComputationalTypeInt)
                case ISTORE.opcode ⇒
                    storeInstruction(as[ISTORE](instruction).lvIndex, ComputationalTypeInt)

                case IRETURN.opcode ⇒
                    val returnedValue =
                        aiResult.flatMap { r ⇒
                            // We have to be able to handle the case that the operands
                            // array is empty (i.e., the instruction is dead)
                            Option(r.operandsArray(pc)).map(ops ⇒ DomainValueBasedVar(0, ops.head))
                        }.getOrElse(OperandVar.IntReturnValue)
                    statements(pc) = List(ReturnValue(pc, returnedValue))
                case LRETURN.opcode ⇒
                    statements(pc) = List(ReturnValue(pc, OperandVar.LongReturnValue))
                case FRETURN.opcode ⇒
                    statements(pc) = List(ReturnValue(pc, OperandVar.FloatReturnValue))
                case DRETURN.opcode ⇒
                    statements(pc) = List(ReturnValue(pc, OperandVar.DoubleReturnValue))
                case ARETURN.opcode ⇒
                    statements(pc) = List(ReturnValue(pc, OperandVar.ReferenceReturnValue))
                case RETURN.opcode ⇒
                    statements(pc) = List(Return(pc))

                case IF_ICMPEQ.opcode | IF_ICMPNE.opcode |
                    IF_ICMPLT.opcode | IF_ICMPLE.opcode |
                    IF_ICMPGT.opcode | IF_ICMPGE.opcode ⇒
                    val ifInstr = as[IFICMPInstruction](instruction)
                    val value2 :: value1 :: rest = stack
                    // let's calculate the final address
                    val targetPC = pc + ifInstr.branchoffset
                    val stmt = If(pc, value1, ifInstr.condition, value2, targetPC)
                    statements(pc) = List(stmt)
                    schedule(pcOfNextInstruction(pc), rest)
                    schedule(targetPC, rest)

                case IFEQ.opcode | IFNE.opcode |
                    IFLT.opcode | IFLE.opcode |
                    IFGT.opcode | IFGE.opcode ⇒
                    val ifInstr = as[IF0Instruction](instruction)
                    val value :: rest = stack
                    // let's calculate the final address
                    val targetPC = pc + ifInstr.branchoffset
                    val stmt = If(pc, value, ifInstr.condition, IntConst(-pc, 0), targetPC)
                    statements(pc) = List(stmt)
                    schedule(pcOfNextInstruction(pc), rest)
                    schedule(targetPC, rest)

                case SWAP.opcode ⇒
                    val value2 :: value1 :: rest = stack
                    val tempVar = TempVar(value2.cTpe)
                    val newValue2 = value2.updated(value1.cTpe)
                    val newValue1 = value1.updated(value2.cTpe)
                    statements(pc) = List(
                        Assignment(pc, tempVar, value2),
                        Assignment(pc, newValue2, value1),
                        Assignment(pc, newValue1, tempVar)
                    )
                    schedule(pcOfNextInstruction(pc), newValue2 :: newValue1 :: rest)

                case DADD.opcode ⇒ binaryArithmeticOperation(Add)
                case DDIV.opcode ⇒ binaryArithmeticOperation(Divide)

                // FIXME 
                //                case DCMPG.opcode ⇒ 
                //                    val value2 :: value1 :: rest = stack
                //                    val nan = Double.NaN
                //                    val result = OperandVar(ComputationalTypeInt, stack)
                //                    statements(pc) = List(
                //                        If(pc, value1, GT, value2, retOnePC)
                ////                        If(pc, value1, EQ, value2, retZeroPC)
                //                        If(pc, value1, LT, value2, retNegOnePC)
                //                        If(pc, value1, EQ, nan, retOnePC)
                //                        
                //                    )
                //                    schedule(pcOfNextInstruction(pc), result :: rest)

                // FIXME case DCMPL.opcode ⇒ arithmeticOperation(Greater)
                case DNEG.opcode ⇒ prefixArithmeticOperation(Negate)
                case DMUL.opcode ⇒ binaryArithmeticOperation(Multiply)
                case DREM.opcode ⇒ binaryArithmeticOperation(Modulo)
                case DSUB.opcode ⇒ binaryArithmeticOperation(Subtract)

                case FADD.opcode ⇒ binaryArithmeticOperation(Add)
                case FDIV.opcode ⇒ binaryArithmeticOperation(Divide)
                // FIXME case FCMPG.opcode ⇒ arithmeticOperation(Greater)
                // FIXME case FCMPL.opcode ⇒ arithmeticOperation(Greater)
                case FNEG.opcode ⇒ prefixArithmeticOperation(Negate)
                case FMUL.opcode ⇒ binaryArithmeticOperation(Multiply)
                case FREM.opcode ⇒ binaryArithmeticOperation(Modulo)
                case FSUB.opcode ⇒ binaryArithmeticOperation(Subtract)

                case IADD.opcode ⇒ binaryArithmeticOperation(Add)
                case IAND.opcode ⇒ binaryArithmeticOperation(And)
                case IDIV.opcode ⇒ binaryArithmeticOperation(Divide)

                case IINC.opcode ⇒
                    val IINC(index, const) = instruction
                    val indexReg = RegisterVar(ComputationalTypeInt, index)
                    statements(pc) = List(
                        Assignment(pc, indexReg,
                            BinaryExpr(pc, ComputationalTypeInt, Add, indexReg, IntConst(pc, const)))
                    )
                    schedule(pcOfNextInstruction(pc), stack)

                case INEG.opcode  ⇒ prefixArithmeticOperation(Negate)
                case IMUL.opcode  ⇒ binaryArithmeticOperation(Multiply)
                case IOR.opcode   ⇒ binaryArithmeticOperation(Or)
                case IREM.opcode  ⇒ binaryArithmeticOperation(Modulo)
                case ISHL.opcode  ⇒ binaryArithmeticOperation(ShiftLeft)
                case ISHR.opcode  ⇒ binaryArithmeticOperation(ShiftRight)
                case ISUB.opcode  ⇒ binaryArithmeticOperation(Subtract)
                case IUSHR.opcode ⇒ binaryArithmeticOperation(UnsignedShiftRight)
                case IXOR.opcode  ⇒ binaryArithmeticOperation(XOr)

                case LADD.opcode  ⇒ binaryArithmeticOperation(Add)
                case LAND.opcode  ⇒ binaryArithmeticOperation(And)
                case LDIV.opcode  ⇒ binaryArithmeticOperation(Divide)
                case LNEG.opcode  ⇒ prefixArithmeticOperation(Negate)
                case LMUL.opcode  ⇒ binaryArithmeticOperation(Multiply)
                case LOR.opcode   ⇒ binaryArithmeticOperation(Or)
                case LREM.opcode  ⇒ binaryArithmeticOperation(Modulo)
                case LSHL.opcode  ⇒ binaryArithmeticOperation(ShiftLeft)
                case LSHR.opcode  ⇒ binaryArithmeticOperation(ShiftRight)
                case LSUB.opcode  ⇒ binaryArithmeticOperation(Subtract)
                case LUSHR.opcode ⇒ binaryArithmeticOperation(UnsignedShiftRight)
                case LXOR.opcode  ⇒ binaryArithmeticOperation(XOr)

                case ICONST_0.opcode | ICONST_1.opcode |
                    ICONST_2.opcode | ICONST_3.opcode |
                    ICONST_4.opcode ⇒
                    val value = as[LoadConstantInstruction[Int]](instruction).value
                    val targetVar = OperandVar(ComputationalTypeInt, stack)
                    statements(pc) = List(Assignment(pc, targetVar, IntConst(pc, value)))
                    schedule(pcOfNextInstruction(pc), targetVar :: stack)

                // TODO Add support for all the other instructions!

                case opcode ⇒
                    throw BytecodeProcessingFailedException(s"unknown opcode: $opcode")
            }
        }

        var index = 0
        val finalStatements = new ArrayBuffer[Stmt](codeSize)
        // Now we have to remap the target pcs to create the final statements array
        // however, before we can do that we first add the register initialization
        // statements

        var registerIndex = 0
        if (!method.isStatic) {
            val targetVar = RegisterVar(ComputationalTypeReference, 0)
            val sourceParam = Param(ComputationalTypeReference, "this")
            finalStatements += Assignment(-1, targetVar, sourceParam)
            index += 1
            registerIndex += 1
        }
        method.descriptor.parameterTypes.foreach { parameterType ⇒
            // TODO use debug information to get better names...
            val cTpe = parameterType.computationalType
            val targetVar = RegisterVar(cTpe, registerIndex)
            val sourceParam = Param(cTpe, "p_"+index)
            finalStatements += Assignment(-1, targetVar, sourceParam)
            index += 1
            registerIndex += cTpe.category
        }

        var currentPC = 0
        val pcToIndex = new Array[Int](codeSize)
        while (currentPC < codeSize) {
            val currentStatements = statements(currentPC)
            if (currentStatements ne null) {
                for (stmt ← currentStatements) {
                    finalStatements += stmt
                    if (pcToIndex(currentPC) == 0 /*the mapping is not yet set; we don't care about the remapping of 0 to 0...*/ )
                        pcToIndex(currentPC) = index
                    index += 1
                }
            }
            currentPC += 1
        }
        finalStatements.foreach(_.remapIndexes(pcToIndex))
        finalStatements.toArray
    }

}

