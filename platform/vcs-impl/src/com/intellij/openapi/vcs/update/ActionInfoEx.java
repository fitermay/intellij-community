/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.execution.ExecutionModes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;

import java.util.LinkedHashMap;

public interface ActionInfoEx extends ActionInfo{

  public static boolean activeToolWindowOnUpdate(ActionInfo ex)
  {
     if (ex instanceof  ActionInfoEx)
     {
        return ((ActionInfoEx)ex).activeToolWindowOnUpdate();
     }

     return true;
  }

  public static PerformInBackgroundOption maybeOverrideBackGroundOption(ActionInfo ex, PerformInBackgroundOption option)
  {
    if (ex instanceof  ActionInfoEx)
    {
      return ((ActionInfoEx)ex).maybeOverrideBackGroundOption(option);
    }

    return option;
  }

  PerformInBackgroundOption maybeOverrideBackGroundOption(PerformInBackgroundOption option);

  public boolean activeToolWindowOnUpdate();
}
