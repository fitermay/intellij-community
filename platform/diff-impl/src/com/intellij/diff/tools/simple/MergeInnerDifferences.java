/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.tools.simple;

import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MergeInnerDifferences {
  @Nullable private final List<TextRange> myLeft;
  @Nullable private final List<TextRange> myBase;
  @Nullable private final List<TextRange> myRight;

  public MergeInnerDifferences(@Nullable List<TextRange> left, @Nullable List<TextRange> base, @Nullable List<TextRange> right) {
    myLeft = left;
    myBase = base;
    myRight = right;
  }

  @Nullable
  public List<TextRange> get(@NotNull ThreeSide side) {
    return side.select(myLeft, myBase, myRight);
  }
}
