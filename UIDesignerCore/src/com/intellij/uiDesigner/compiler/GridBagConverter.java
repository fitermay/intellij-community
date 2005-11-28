/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.core.GridConstraints;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 24.11.2005
 * Time: 12:56:00
 * To change this template use File | Settings | File Templates.
 * @noinspection ForLoopReplaceableByForEach, unchecked
 */
public class GridBagConverter {
  private Insets myInsets;
  private int myHGap;
  private int myVGap;
  private boolean mySameSizeHorz;
  private boolean mySameSizeVert;
  private ArrayList myComponents = new ArrayList();
  private ArrayList myConstraints = new ArrayList();
  private int myLastRow = -1;
  private int myLastCol = -1;

  public GridBagConverter() {
    myInsets = new Insets(0, 0, 0, 0);
  }

  public GridBagConverter(final Insets insets, final int hgap, final int vgap, final boolean sameSizeHorz, final boolean sameSizeVert) {
    myInsets = insets;
    myHGap = hgap;
    myVGap = vgap;
    mySameSizeHorz = sameSizeHorz;
    mySameSizeVert = sameSizeVert;
  }

  public void addComponent(final JComponent component, final GridConstraints constraints) {
    myComponents.add(component);
    myConstraints.add(constraints);
  }

  public static class Result {
    public JComponent component;
    public boolean isFillerPanel;
    public GridBagConstraints constraints;
    public Dimension preferredSize;
    public Dimension minimumSize;
    public Dimension maximumSize;

    public Result(final JComponent component) {
      this.component = component;
      constraints = new GridBagConstraints();
    }
  }

  public Result[] convert() {
    ArrayList results = new ArrayList();
    for(int i=0; i<myComponents.size(); i++) {
      results.add(convert((JComponent) myComponents.get(i), (GridConstraints) myConstraints.get(i)));
    }
    //addFillerPanels(results);
    final Result[] resultArray = (Result[])results.toArray(new Result[results.size()]);
    if (myHGap > 0 || myVGap > 0) {
      applyGaps(resultArray);
    }
    if (mySameSizeHorz) {
      makeSameSizes(resultArray, true);
    }
    if (mySameSizeVert) {
      makeSameSizes(resultArray, false);
    }

    return resultArray;
  }

  private void applyGaps(final Result[] resultArray) {
    int leftGap = myHGap/2;
    int rightGap = myHGap - myHGap/2;
    int topGap = myVGap / 2;
    int bottomGap = myVGap - myVGap/2;
    for(int i=0; i<resultArray.length; i++) {
      Result result = resultArray [i];
      if (result.constraints.gridx > 0) {
        result.constraints.insets.left += leftGap;
      }
      if (result.constraints.gridx + result.constraints.gridwidth-1 < myLastCol) {
        result.constraints.insets.right += rightGap;
      }
      if (result.constraints.gridy > 0) {
        result.constraints.insets.top += topGap;
      }
      if (result.constraints.gridy + result.constraints.gridheight-1 < myLastRow) {
        result.constraints.insets.bottom += bottomGap;
      }
    }
  }

  private void makeSameSizes(final Result[] resultArray, boolean horizontal) {
    int minimum = -1, preferred=-1;
    for(int i=0; i<resultArray.length; i++) {
      Result result = resultArray [i];
      Dimension minSize = result.minimumSize != null || result.component == null
                          ? result.minimumSize
                          : result.component.getMinimumSize();
      Dimension prefSize = result.preferredSize != null || result.component == null
                          ? result.preferredSize
                          : result.component.getPreferredSize();
      if (minSize != null) {
        minimum = Math.max(minimum, horizontal ? minSize.width : minSize.height);
      }
      if (prefSize != null) {
        preferred = Math.max(preferred, horizontal ? prefSize.width : prefSize.height);
      }
    }

    if (minimum >= 0 || preferred >= 0) {
      for(int i=0; i<resultArray.length; i++) {
        Result result = resultArray [i];

        if ((result.minimumSize != null || result.component != null) && minimum >= 0) {
          if (result.minimumSize == null) {
            result.minimumSize = result.component.getMinimumSize();
          }
          if (horizontal) {
            result.minimumSize.width = minimum;
          }
          else {
            result.minimumSize.height = minimum;
          }
        }

        if ((result.preferredSize != null || result.component != null) && preferred >= 0) {
          if (result.preferredSize == null) {
            result.preferredSize = result.component.getPreferredSize();
          }
          if (horizontal) {
            result.preferredSize.width = preferred;
          }
          else {
            result.preferredSize.height = preferred;
          }
        }
      }
    }
  }

  private void addFillerPanels(final ArrayList results) {
    for(int row=0; row<=myLastRow; row++) {
      for(int col=0; col<=myLastCol; col++) {
        if (isCellEmpty(row, col)) {
          Result result = new Result(null);
          result.constraints.gridx = col;
          result.constraints.gridy = row;
          result.constraints.gridwidth = 1;
          result.constraints.gridheight = 1;
          result.constraints.weightx = 0.0;
          result.constraints.weighty = 0.0;
          result.constraints.fill = GridBagConstraints.BOTH;
          result.isFillerPanel = true;
          results.add(result);
        }
      }
    }
  }

  private Result convert(final JComponent component, final GridConstraints constraints) {
    final Result result = new Result(component);

    int endRow = constraints.getRow() + constraints.getRowSpan()-1;
    myLastRow = Math.max(myLastRow, endRow);
    int endCol = constraints.getColumn() + constraints.getColSpan()-1;
    myLastCol = Math.max(myLastCol, endCol);

    result.constraints.gridx = constraints.getColumn();
    result.constraints.gridy = constraints.getRow();
    result.constraints.gridwidth = constraints.getColSpan();
    result.constraints.gridheight = constraints.getRowSpan();
    result.constraints.weightx = getWeight(constraints, true);
    result.constraints.weighty = getWeight(constraints, false);
    result.constraints.insets = new Insets(myInsets.top, myInsets.left, myInsets.bottom, myInsets.right);
    switch(constraints.getFill()) {
      case GridConstraints.FILL_HORIZONTAL: result.constraints.fill = GridBagConstraints.HORIZONTAL; break;
      case GridConstraints.FILL_VERTICAL:   result.constraints.fill = GridBagConstraints.VERTICAL; break;
      case GridConstraints.FILL_BOTH:       result.constraints.fill = GridBagConstraints.BOTH; break;
    }
    switch(constraints.getAnchor()) {
      case GridConstraints.ANCHOR_NORTHWEST: result.constraints.anchor = GridBagConstraints.NORTHWEST; break;
      case GridConstraints.ANCHOR_NORTH:     result.constraints.anchor = GridBagConstraints.NORTH; break;
      case GridConstraints.ANCHOR_NORTHEAST: result.constraints.anchor = GridBagConstraints.NORTHEAST; break;
      case GridConstraints.ANCHOR_EAST:      result.constraints.anchor = GridBagConstraints.EAST; break;
      case GridConstraints.ANCHOR_SOUTHEAST: result.constraints.anchor = GridBagConstraints.SOUTHEAST; break;
      case GridConstraints.ANCHOR_SOUTH:     result.constraints.anchor = GridBagConstraints.SOUTH; break;
      case GridConstraints.ANCHOR_SOUTHWEST: result.constraints.anchor = GridBagConstraints.SOUTHWEST; break;
      case GridConstraints.ANCHOR_WEST:      result.constraints.anchor = GridBagConstraints.WEST; break;
    }

    Dimension minSize = constraints.myMinimumSize;
    if (component != null && minSize.width <= 0 && minSize.height <= 0) {
      minSize = component.getMinimumSize();
    }

    if ((constraints.getHSizePolicy() & GridConstraints.SIZEPOLICY_CAN_SHRINK) == 0) {
      minSize.width = constraints.myPreferredSize.width > 0 || component == null
                      ? constraints.myPreferredSize.width
                      : component.getPreferredSize().width;
    }
    if ((constraints.getVSizePolicy() & GridConstraints.SIZEPOLICY_CAN_SHRINK) == 0) {
      minSize.height = constraints.myPreferredSize.height > 0 || component == null
                       ? constraints.myPreferredSize.height
                       : component.getPreferredSize().height;
    }

    if (minSize.width != -1 || minSize.height != -1) {
      result.minimumSize = minSize;
    }

    if (constraints.myPreferredSize.width > 0 && constraints.myPreferredSize.height > 0) {
      result.preferredSize = constraints.myPreferredSize;
    }
    if (constraints.myMaximumSize.width > 0 && constraints.myMaximumSize.height > 0) {
      result.maximumSize = constraints.myMaximumSize;
    }

    return result;
  }

  private double getWeight(final GridConstraints constraints, final boolean horizontal) {
    int policy = horizontal ? constraints.getHSizePolicy() : constraints.getVSizePolicy();
    if ((policy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
      return 1.0;
    }
    boolean canGrow = ((policy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0);
    for (Iterator iterator = myConstraints.iterator(); iterator.hasNext();) {
      GridConstraints otherConstraints = (GridConstraints)iterator.next();
      if (otherConstraints != constraints) {
        boolean sameRow = horizontal
                      ? otherConstraints.getRow() == constraints.getRow()
                      : otherConstraints.getColumn() == constraints.getColumn();
        if (sameRow) {
          int otherPolicy = horizontal ? otherConstraints.getHSizePolicy() : otherConstraints.getVSizePolicy();
          if ((otherPolicy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0) {
            return 0.0;
          }
          if (!canGrow && ((otherPolicy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0)) {
            return 0.0;
          }
        }
      }
    }
    return 1.0;
  }

  private boolean isCellEmpty(final int row, final int col) {
    for (Iterator iterator = myConstraints.iterator(); iterator.hasNext();) {
      GridConstraints gridConstraints = (GridConstraints)iterator.next();
      if (row >= gridConstraints.getRow() &&
          row < gridConstraints.getRow() + gridConstraints.getRowSpan() &&
          col >= gridConstraints.getColumn() &&
          col < gridConstraints.getColumn() + gridConstraints.getColSpan()) {
        return false;
      }
    }
    return true;
  }

}
