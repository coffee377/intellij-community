/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfaMemoryState {
  @NotNull
  DfaMemoryState createCopy();

  @NotNull
  DfaMemoryState createClosureState();

  /**
   * Pops single value from the top of the stack and returns it
   * @return popped value
   * @throws com.intellij.codeInspection.dataFlow.instructions.EmptyStackInstruction if stack is empty
   */
  @NotNull DfaValue pop();

  /**
   * Reads a value from the top of the stack without popping it
   * @return top of stack value
   * @throws com.intellij.codeInspection.dataFlow.instructions.EmptyStackInstruction if stack is empty
   */
  @NotNull DfaValue peek();

  /**
   * Pushes given value to the stack
   * @param value to push
   */
  void push(@NotNull DfaValue value);

  void emptyStack();

  void setVarValue(DfaVariableValue var, DfaValue value);

  /**
   * Ensures that top-of-stack value is either null or belongs to the supplied type
   *
   * @param type the type to cast to
   * @return true if cast is successful; false if top-of-stack value type is incompatible with supplied type
   * @throws com.intellij.codeInspection.dataFlow.instructions.EmptyStackInstruction if stack is empty
   */
  boolean castTopOfStack(@NotNull DfaPsiType type);

  boolean applyCondition(DfaValue dfaCond);

  boolean applyContractCondition(DfaValue dfaCond);

  /**
   * Returns a value fact about supplied value within the context of current memory state.
   * Returns null if the fact of given type is not known or not applicable to a given value.
   *
   * @param factType a type of the fact to get
   * @param value a value to get the fact about
   * @param <T> a type of the fact value
   * @return a fact about value, if known
   */
  @Nullable
  <T> T getValueFact(@NotNull DfaFactType<T> factType, @NotNull DfaValue value);

  void forceNotNull(@NotNull DfaVariableValue var);

  void flushFields();

  void flushVariable(@NotNull DfaVariableValue variable);

  boolean isNull(DfaValue dfaVar);

  boolean checkNotNullable(DfaValue value);

  boolean isNotNull(DfaValue dfaVar);

  @Nullable
  DfaConstValue getConstantValue(@NotNull DfaVariableValue value);

  /**
   * Ephemeral means a state that was created when considering a method contract and checking if one of its arguments is null.
   * With explicit null check, that would result in any non-annotated variable being treated as nullable and producing possible NPE warnings later.
   * With contracts, we don't want this. So the state where this variable is null is marked ephemeral and no NPE warnings are issued for such states. 
   */
  void markEphemeral();
  
  boolean isEphemeral();

  boolean isEmptyStack();
}
