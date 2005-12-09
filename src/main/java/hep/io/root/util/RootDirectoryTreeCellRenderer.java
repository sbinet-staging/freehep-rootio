package hep.io.root.util;

import hep.io.root.RootClass;
import hep.io.root.interfaces.TBranch;
import hep.io.root.interfaces.TBranchClones;
import hep.io.root.interfaces.TBranchObject;
import hep.io.root.interfaces.TFile;
import hep.io.root.interfaces.TKey;
import hep.io.root.interfaces.TNamed;
import hep.io.root.interfaces.TTree;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;


/**
 * A TreeCellRenderer for Root TNamed objects.
 * @author Tony Johnson (tonyj@slac.stanford.edu)
 * @version $Id$
 */
public class RootDirectoryTreeCellRenderer extends DefaultTreeCellRenderer
{
   private final static Icon h1Icon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/h1_t.gif"));
   private final static Icon h2Icon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/h2_t.gif"));
   private final static Icon h3Icon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/h3_t.gif"));
   private final static Icon rootdbIcon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/rootdb_t.gif"));
   private final static Icon treeIcon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/tree_t.gif"));
   private final static Icon branchIcon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/branch_t.gif"));
   private final static Icon branchObIcon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/branch-ob_t.gif"));
   private final static Icon branchClIcon = new ImageIcon(RootDirectoryTreeCellRenderer.class.getResource("images/branch-cl_t.gif"));

   public java.awt.Component getTreeCellRendererComponent(JTree p1, Object p2, boolean p3, boolean p4, boolean p5, int p6, boolean p7)
   {
      super.getTreeCellRendererComponent(p1, p2, p3, p4, p5, p6, p7);
      if (p2 instanceof TKey)
      {
         TKey key = (TKey) p2;
         setText(key.getTitle() + " (" + key.getName() + ";" + key.getCycle() + ")");
      }
      else if (p2 instanceof TNamed)
      {
         TNamed named = (TNamed) p2;
         setText(named.getTitle() + " (" + named.getName() + ")");
      }
      if (p2 instanceof TBranchObject)
         setIcon(branchObIcon);
      else if (p2 instanceof TBranchClones)
         setIcon(branchClIcon);
      else if (p2 instanceof TBranch)
         setIcon(branchIcon);
      else if (p2 instanceof TKey)
      {
         try
         {
            RootClass rc = ((TKey) p2).getObjectClass();
            Class jc = rc.getJavaClass();
            if (TTree.class.isAssignableFrom(jc))
               setIcon(treeIcon);

            //            else if (rc.instanceOf("TH2"))   setIcon(h2Icon);
            //            else if (rc.instanceOf("TH3"))   setIcon(h3Icon);
            //            else if (rc.instanceOf("TH1"))   setIcon(h1Icon);
            else if (TFile.class.isAssignableFrom(jc))
               setIcon(rootdbIcon);
         }
         catch (java.io.IOException x) {}
         catch (Throwable x) {}
      }
      return this;
   }
}
