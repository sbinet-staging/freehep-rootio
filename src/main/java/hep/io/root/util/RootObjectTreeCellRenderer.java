/*
 * RootObjectTreeCellRenderer.java
 *
 * Created on January 18, 2001, 2:43 PM
 */
package hep.io.root.util;

import javax.swing.*;
import javax.swing.tree.*;


/**
 * A TreeCellRenderer for use with RootObjectTreeModel.
 * Deals with setting tool tips on the tree
 * @author  tonyj
 * @version $Id$
 */
public class RootObjectTreeCellRenderer extends DefaultTreeCellRenderer
{
   public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean p3, boolean p4, boolean p5, int p6, boolean p7)
   {
      if (value instanceof RootObjectTreeModel.RootObjectTreeNode)
      {
         setToolTipText(((RootObjectTreeModel.RootObjectTreeNode) value).toolTip());
      }
      return super.getTreeCellRendererComponent(tree, value, p3, p4, p5, p6, p7);
   }
}
