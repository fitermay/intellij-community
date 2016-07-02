/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.google.common.collect.Sets;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * @author vlan
 */
public class PyDynamicallyEvaluatedType extends PyUnionType {
  private PyDynamicallyEvaluatedType(@NotNull LinkedHashSet<PyType> members) {
    super(members, true);
  }

  @NotNull
  public static PyDynamicallyEvaluatedType create(@NotNull PyType type) {

    return new PyDynamicallyEvaluatedType(type instanceof PyUnionType ? ((PyUnionType)type).myMembers : Sets.newLinkedHashSet(
      Collections.singleton(type)));
  }

  @Override
  public String getName() {
    PyType res = excludeNull(TypeEvalContext.codeInsightFallback(null));
    return res != null ? res.getName() : PyNames.UNKNOWN_TYPE;
  }
}
