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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.NullableFunction;
import com.intellij.util.ProcessingContext;
import com.intellij.util.SmartList;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyUnionType implements PyType {
  protected final LinkedHashSet<PyType> myMembers;
  private boolean myIsWeak;

  protected PyUnionType(LinkedHashSet<PyType> members, boolean isWeak) {
    myMembers = members;
    myIsWeak = isWeak;
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    SmartList<RatedResolveResult> ret = new SmartList<RatedResolveResult>();
    boolean allNulls = true;
    for (PyType member : myMembers) {
      List<? extends RatedResolveResult> result = member.resolveMember(name, location, direction, resolveContext);
      if (result != null) {
        allNulls = false;
        ret.addAll(result);
      }
    }
    return allNulls ? null : ret;
  }

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    Set<Object> variants = new HashSet<Object>();
    for (PyType member : myMembers) {
      Collections.addAll(variants, member.getCompletionVariants(completionPrefix, location, context));
    }
    return variants.toArray(new Object[variants.size()]);
  }

  public String getName() {
    return StringUtil.join(getMembers(), (NullableFunction<PyType, String>)type -> type != null ? type.getName() : null, " | ");
  }

  /**
   * @return true if all types in the union are built-in.
   */
  @Override
  public boolean isBuiltin() {

    if (myIsWeak) {
      return false;
    }
    for (PyType one : myMembers) {
      if (!one.isBuiltin()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void assertValid(String message) {
    for (PyType member : myMembers) {
      member.assertValid(message);
    }
  }

  @Nullable
  public static PyType union(@Nullable PyType type1, @Nullable PyType type2) {
    if (type1 instanceof PyUnionType && type2 instanceof PyUnionType) {
      return joinTwoUnionTypes((PyUnionType)type1, (PyUnionType)type2);
    }
    else if ((type1 instanceof PyUnionType || type2 instanceof PyUnionType)) {
      return (type1 instanceof PyUnionType)
             ? joinUnionAndRegular((PyUnionType)type1, type2, false)
             : joinUnionAndRegular((PyUnionType)type2, type1, true);
    }
    else {
      return joinNonUnionTypes(type1, type2);
    }
  }

  private static PyType joinNonUnionTypes(@Nullable PyType type1, @Nullable PyType type2) {
    if (type1 == null && type2 == null) {
      return null;
    }
    if (type1 == null || type2 == null) {
      return createWeakType(type1 == null ? type2 : type1);
    }
    else if (type1.equals(type2)) {
      return type1;
    }
    else if ((type1 == PyNoneType.WEAK_INSTANCE || type2 == PyNoneType.WEAK_INSTANCE) &&
             (type1 == PyNoneType.INSTANCE || type2 == PyNoneType.INSTANCE)) { //special case
      return PyNoneType.WEAK_INSTANCE;
    }
    else {
      boolean isWeak = (type1 == PyNoneType.WEAK_INSTANCE || type2 == PyNoneType.WEAK_INSTANCE);
      LinkedHashSet<PyType> set = Sets.newLinkedHashSet();
      set.add((type1 == PyNoneType.WEAK_INSTANCE) ? PyNoneType.INSTANCE : type1);
      set.add((type2 == PyNoneType.WEAK_INSTANCE) ? PyNoneType.INSTANCE : type2);
      return new PyUnionType(set, isWeak);
    }
  }

  private static PyType joinUnionAndRegular(@NotNull PyUnionType type1, @Nullable PyType type2, boolean flipOrder) {
    if (type2 != null && type1.myMembers.contains(type2)) {
      return type1;
    }
    else if (type2 == null) {
      return new PyUnionType(type1.myMembers, true);
    }
    else {
      Iterable<PyType> concatenated = flipOrder ? Iterables.concat(Collections.singletonList(type2), type1.myMembers) :
                                      Iterables.concat(type1.myMembers, Collections.singletonList(type2));
      return new PyUnionType(Sets.newLinkedHashSet(concatenated), type1.myIsWeak || type2 == PyNoneType.WEAK_INSTANCE);
    }
  }

  private static PyUnionType joinTwoUnionTypes(@NotNull PyUnionType type1, @NotNull PyUnionType type2) {
    LinkedHashSet<PyType> members1 = type1.myMembers;
    LinkedHashSet<PyType> members2 = type2.myMembers;
    boolean isWeak = type1.myIsWeak || type2.myIsWeak;
    if (members1.size() < members2.size() && members2.containsAll(members1)) {
      return new PyUnionType(members2, isWeak);
    }
    else if (members1.containsAll(members2)) {
      return new PyUnionType(members1, isWeak);
    }
    else {
      LinkedHashSet<PyType> joinedSet = Sets.newLinkedHashSet(Iterables.concat(members1, members2));
      return new PyUnionType(joinedSet, isWeak);
    }
  }

  @Nullable
  public static PyType union(Collection<PyType> members) {
    final int n = members.size();
    if (n == 0) {
      return null;
    }
    else if (n == 1) {
      return members.iterator().next();
    }
    else {
      final Iterator<PyType> it = members.iterator();
      PyType res = it.next();
      while (it.hasNext()) {
        res = union(res, it.next());
      }
      return res;
    }
  }

  @Nullable
  public static PyType createWeakType(@Nullable PyType type) {
    if (type == null) {
      return null;
    }
    else if (type instanceof PyUnionType) {
      final PyUnionType unionType = (PyUnionType)type;
      if (unionType.isWeak()) {
        return unionType;
      }
      else {
        return new PyUnionType(((PyUnionType)type).myMembers, true);
      }
    }
    else if (type instanceof PyNoneType) {
      return PyNoneType.WEAK_INSTANCE;
    }
    return new PyUnionType(Sets.newLinkedHashSet(Collections.singleton(type)), true);
  }

  public boolean isWeak() {
    return myIsWeak;
  }

  public Iterable<PyType> getMembers() {
    //Preserve the null member behavior for outside world
    return !isWeak() ? Collections.unmodifiableSet(myMembers) : Iterables.concat(Collections.singletonList(null), myMembers);
  }

  /**
   * Excludes all subtypes of type from the union
   *
   * @param type    type to exclude. If type is a union all subtypes of union members will be excluded from the union
   *                If type is null only null will be excluded from the union.
   * @param context
   * @return union with excluded types
   */
  @Nullable
  public PyType exclude(@Nullable PyType type, @NotNull TypeEvalContext context) {
    final List<PyType> members = new ArrayList<PyType>();
    for (PyType m : getMembers()) {
      if (type == null) {
        if (m != null) {
          members.add(m);
        }
      }
      else {
        if (!PyTypeChecker.match(type, m, context)) {
          members.add(m);
        }
      }
    }
    return union(members);
  }

  @Nullable
  public PyType excludeNull(@NotNull TypeEvalContext context) {
    if (myIsWeak) {

      return myMembers.size() > 1 ? new PyUnionType(myMembers, false) : myMembers.iterator().next();
    }
    else {
      return this;
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyUnionType)) return false;

    PyUnionType type = (PyUnionType)o;

    if (myIsWeak != type.myIsWeak) return false;
    if (!myMembers.equals(type.myMembers)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMembers.hashCode();
    result = 31 * result + (myIsWeak ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "PyUnionType: " + getName();
  }
}
