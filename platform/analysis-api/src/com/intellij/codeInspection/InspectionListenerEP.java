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
package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public interface InspectionListenerEP {

  public static final ExtensionPointName<InspectionListenerEP> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.inspectionListener");

  static void notifySessionBegin(LocalInspectionToolSession session) {
    Stream.of(Extensions.getExtensions(EXTENSION_POINT_NAME)).forEach(e -> e.beginSession(session));
  }

  static void notifySessionEnd(LocalInspectionToolSession session) {
    Stream.of(Extensions.getExtensions(EXTENSION_POINT_NAME)).forEach(e -> e.endSession(session));
  }

  void beginSession(@NotNull LocalInspectionToolSession psiFile);

  public void endSession(@NotNull LocalInspectionToolSession psiFile);
}
