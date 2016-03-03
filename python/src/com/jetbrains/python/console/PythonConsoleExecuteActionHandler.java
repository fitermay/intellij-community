package com.jetbrains.python.console;

import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.process.ProcessHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Yuli Fiterman on 3/3/2016.
 */
public abstract class PythonConsoleExecuteActionHandler extends ProcessBackedConsoleExecuteActionHandler {
  public PythonConsoleExecuteActionHandler(ProcessHandler processHandler, boolean preserveMarkup) {
    super(processHandler, preserveMarkup);
  }

  public static String getPrevCommandRunningMessage() {
    return "Previous command is still running. Please wait or press Ctrl+C in console to interrupt.";
  }

  @Override
  public abstract void processLine(@NotNull String text);

  public abstract void processLine(@NotNull String text, boolean execAnyway);

  public abstract int getCurrentIndentSize();

  public abstract int getPythonIndent();

  public abstract String getCantExecuteMessage();

  public abstract boolean canExecuteNow();

  public abstract void setEnabled(boolean flag);

  public abstract boolean isEnabled();

  public abstract ConsoleCommunication getConsoleCommunication();
}
