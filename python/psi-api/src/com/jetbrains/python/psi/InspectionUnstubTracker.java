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
package com.jetbrains.python.psi;

import com.intellij.codeInspection.InspectionListenerEP;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;

public class InspectionUnstubTracker extends AbstractProjectComponent {

  public static class InspectionTracerEP implements InspectionListenerEP {

    @Override
    public void beginSession(@NotNull LocalInspectionToolSession session) {
      getInstance(session.getFile().getProject()).beginSession(session.getFile());
    }

    @Override
    public void endSession(@NotNull LocalInspectionToolSession session) {
      getInstance(session.getFile().getProject()).endSession();
    }
  }

  private static final Logger LOG = Logger.getInstance(InspectionUnstubTracker.class.getName());

  public static InspectionUnstubTracker getInstance(Project project) {
    return project.getComponent(InspectionUnstubTracker.class);
  }

  public InspectionUnstubTracker(Project project, PsiManagerEx myManager) {
    super(project);

    myManager.setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.info("Unstubbing " + file.getPresentableUrl());
          onUnstub(file);
        }
        return false;
      }
    }, project);
  }

  ThreadLocal<VirtualFile> sessionFile = new ThreadLocal<>();

  public void beginSession(@NotNull PsiFile psiFile) {
    if (psiFile.isPhysical()) {
      sessionFile.set(psiFile.getVirtualFile());
    }
  }

  public void endSession() {
    sessionFile.set(null);
  }

  private void onUnstub(VirtualFile file) {
    VirtualFile session = sessionFile.get();
    if (session == null) {
      return;
    }
    if (!"py".equalsIgnoreCase(file.getExtension())) {
      return;
    }

    if (!file.equals(session)) {
      LOG.info("Unstubbing detected in inspection ", new Throwable());
    }
  }
}
