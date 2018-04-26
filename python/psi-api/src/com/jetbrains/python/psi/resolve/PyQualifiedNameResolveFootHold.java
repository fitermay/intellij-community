// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyQualifiedNameResolveFootHold {

  @NotNull
  Project getProject();

  @NotNull
  PsiManager getPsiManager();

  @Nullable
  PsiElement getFoothold();

  @Nullable
  Sdk getEffectiveSdk();

  @Nullable
  PsiFile getFootholdFile();

  @Nullable
  Sdk getSdk();

  @Nullable
  Module getModule();

  @Nullable
  PsiDirectory getContainingDirectory();

  boolean isValid();
}
