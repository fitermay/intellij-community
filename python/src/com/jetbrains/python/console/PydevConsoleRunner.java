/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.EncodingEnvironmentUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actions.SplitLineAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.remote.RemoteProcess;
import com.intellij.remote.Tunnelable;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.MessageCategory;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PySourcePosition;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSdkCredentials;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.*;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.apache.xmlrpc.XmlRpcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

/**
 * @author oleg
 */
public abstract class PydevConsoleRunner extends AbstractConsoleRunnerWithHistory<PythonConsoleView> {
  public static final String WORKING_DIR_ENV = "WORKING_DIR_AND_PYTHON_PATHS";
  public static final String CONSOLE_START_COMMAND = "import sys; print('Python %s on %s' % (sys.version, sys.platform))\n" +
                                                     "sys.path.extend([" + WORKING_DIR_ENV + "])\n";
  private static final Logger LOG = Logger.getInstance(PydevConsoleRunner.class.getName());
  @SuppressWarnings("SpellCheckingInspection")
  public static final String PYDEV_PYDEVCONSOLE_PY = "pydev/pydevconsole.py";
  public static final int PORTS_WAITING_TIMEOUT = 20000;
  private static final String CONSOLE_FEATURE = "python.console";

  @NotNull
  private Sdk mySdk;
  private GeneralCommandLine myGeneralCommandLine;
  protected int[] myPorts;
  private PydevConsoleCommunication myPydevConsoleCommunication;
  private PyConsoleProcessHandler myProcessHandler;
  protected PydevConsoleExecuteActionHandler myConsoleExecuteActionHandler;
  private List<ConsoleListener> myConsoleListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final PyConsoleType myConsoleType;
  private Map<String, String> myEnvironmentVariables;
  private String myCommandLine;
  @NotNull private final PyConsoleOptions.PyConsoleSettings myConsoleSettings;
  private String[] myStatementsToExecute = ArrayUtil.EMPTY_STRING_ARRAY;

  public static Key<ConsoleCommunication> CONSOLE_KEY = new Key<ConsoleCommunication>("PYDEV_CONSOLE_KEY");

  public static Key<Sdk> CONSOLE_SDK = new Key<Sdk>("PYDEV_CONSOLE_SDK_KEY");

  private static final long APPROPRIATE_TO_WAIT = 60000;

  private PyRemoteProcessHandlerBase myRemoteProcessHandlerBase;

  private String myConsoleTitle = null;

  public PydevConsoleRunner(@NotNull final Project project,
                            @NotNull Sdk sdk,
                            @NotNull final PyConsoleType consoleType,
                            @Nullable final String workingDir,
                            Map<String, String> environmentVariables,
                            @NotNull
                            PyConsoleOptions.PyConsoleSettings settingsProvider,
                            String... statementsToExecute) {
    super(project, consoleType.getTitle(), workingDir);
    mySdk = sdk;
    myConsoleType = consoleType;
    myEnvironmentVariables = environmentVariables;
    myConsoleSettings = settingsProvider;
    myStatementsToExecute = statementsToExecute;
  }

  @Nullable
  public static PyRemotePathMapper getPathMapper(@NotNull Project project, Sdk sdk, PyConsoleOptions.PyConsoleSettings consoleSettings) {
    if (PySdkUtil.isRemote(sdk)) {
      PythonRemoteInterpreterManager instance = PythonRemoteInterpreterManager.getInstance();
      if (instance != null) {
        //noinspection ConstantConditions
        PyRemotePathMapper remotePathMapper =
          instance.setupMappings(project, (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData(), null);

        PathMappingSettings mappingSettings = consoleSettings.getMappingSettings();

        remotePathMapper.addAll(mappingSettings.getPathMappings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);

        return remotePathMapper;
      }
    }
    return null;
  }

  @NotNull
  public static Pair<Sdk, Module> findPythonSdkAndModule(@NotNull Project project, @Nullable Module contextModule) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptions.PyConsoleSettings settings = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkType.findSdkByPath(sdkHome);
      if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      else {
        module = contextModule;
        if (module == null && ModuleManager.getInstance(project).getModules().length > 0) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
      }
    }
    if (sdk == null && settings.isUseModuleSdk()) {
      if (contextModule != null) {
        module = contextModule;
      }
      else if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      if (module != null) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkType.findPythonSdk(module);
      }
    }

    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkType.findPythonSdk(m) != null) {
          sdk = PythonSdkType.findPythonSdk(m);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkType.getAllSdks().size() > 0) {
        //noinspection UnusedAssignment
        sdk = PythonSdkType.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  public static String constructPythonPathCommand(Collection<String> pythonPath, String command) {
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + input.replace("\\", "\\\\").replace("'", "\\'") + "'";
      }
    }));

    return command.replace(WORKING_DIR_ENV, path);
  }

  public static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs, @NotNull Project project) {
    setCorrectStdOutEncoding(envs, project);

    PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
  }

  /**
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param envs    map of envs to add variable
   * @param project current project
   */
  public static void setCorrectStdOutEncoding(@NotNull final Map<String, String> envs, @NotNull final Project project) {
    final Charset defaultCharset = getProjectDefaultCharset(project);
    final String encoding = defaultCharset.name();
    setPythonIOEncoding(setPythonUnbuffered(envs), encoding);
  }

  @NotNull
  private static Charset getProjectDefaultCharset(@NotNull Project project) {
    return EncodingProjectManager.getInstance(project).getDefaultCharset();
  }

  public static boolean isInPydevConsole(final PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  public static boolean isPythonConsole(@Nullable ASTNode element) {
    return getPythonConsoleData(element) != null;
  }

  @Nullable
  public static PythonConsoleData getPythonConsoleData(@Nullable ASTNode element) {
    if (element == null || element.getPsi() == null || element.getPsi().getContainingFile() == null) {
      return null;
    }

    VirtualFile file = getConsoleFile(element.getPsi().getContainingFile());

    if (file == null) {
      return null;
    }
    return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA);
  }

  private static VirtualFile getConsoleFile(PsiFile psiFile) {
    VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    if (file instanceof LightVirtualFile) {
      file = ((LightVirtualFile)file).getOriginalFile();
    }
    return file;
  }

  @Nullable
  public static ConsoleCommunication getConsoleCommunication(final PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_KEY) : null;
  }

  public abstract void  runSync();

  public abstract void open();

  public abstract void run();

  @Nullable
  public static Sdk getConsoleSdk(final PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_SDK) : null;
  }

  public abstract void addConsoleListener(ConsoleListener consoleListener);

  public interface ConsoleListener {
    void handleConsoleInitialized(LanguageConsoleView consoleView);
  }
}
