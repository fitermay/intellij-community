package org.hanuna.gitalk.ui;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.data.rebase.VcsLogActionHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;

/**
 * @author erokhins
 */
public interface VcsLogController {

  TableModel getGraphTableModel();

  void readNextPart();

  @NotNull
  InteractiveRebaseBuilder getInteractiveRebaseBuilder();

  @NotNull
  VcsLogActionHandler getVcsLogActionHandler();

  DataPack getDataPack();

  Project getProject();

  void applyInteractiveRebase();

  VcsLogActionHandler.Callback getCallback();

  void cancelInteractiveRebase();

  boolean isInteractiveRebaseInProgress();

  @NotNull
  JComponent getMainComponent();

  void refresh();
}
