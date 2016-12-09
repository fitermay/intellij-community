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
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.InvocationTargetException
import javax.swing.SwingUtilities

class EdtTestUtil {
  companion object {
    @TestOnly @JvmStatic fun runInEdtAndWait(runnable: ThrowableRunnable<Throwable>) {
      runInEdtAndWait { runnable.run() }
    }

    /**
     * Same as {@link #runInEdtAndWait}, but when an unchecked exception happens inside runnable, its just rethrown without being wrapped.
     * Can be useful to decrease "Caused by" chain for cases when the stack trace of the calling thread is not important or obvious.
     */
    @TestOnly @JvmStatic fun runInEdtAndWaitRethrowing(runnable: ThrowableRunnable<Throwable>) {
      var exception: Throwable? = null
      runInEdtAndWait {
        try {
          runnable.run()
        } catch (e: Throwable) {
          exception = e
        }
      }
      ExceptionUtil.rethrowAllAsUnchecked(exception)
    }
  }
}

@TestOnly
fun runInEdtAndWait(runnable: () -> Unit) {
  val application = ApplicationManager.getApplication()
  if (application is ApplicationImpl) {
    application.invokeAndWait(runnable)
    return
  }

  if (SwingUtilities.isEventDispatchThread()) {
    runnable()
  }
  else {
    try {
      SwingUtilities.invokeAndWait(runnable)
    }
    catch (e: InvocationTargetException) {
      throw e.cause ?: e
    }
  }
}