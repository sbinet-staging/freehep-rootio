package hep.io.root.daemon;

import java.net.InetAddress;
import java.net.PasswordAuthentication;

/**
 *
 * @author Tony Johnson
 */
class AuthentificationPanel extends javax.swing.JPanel
{
   
   /** Creates new form AuthentificationPanel */
   AuthentificationPanel(String scheme)
   {
      initComponents();
      boolean anon = scheme != null && scheme.equalsIgnoreCase("anonymous");
      schemeComboBox.setSelectedIndex(anon ? 0 : 1);
   }
   PasswordAuthentication getPasswordAuthentication()
   {
      String username = userTextField.getText();
      char[] password = passwordTextField.getPassword();
      boolean anon = schemeComboBox.getSelectedIndex() == 0;
      if (anon)
      {
         username = "anonymous";
         try
         {
            password = (System.getProperty("user.name")+"@"+InetAddress.getLocalHost().getCanonicalHostName()).toCharArray();
         }
         catch (Throwable x)
         {
            password = "freehep-user@freehep.org".toCharArray();
         }
      }
      return new PasswordAuthentication(username,password);
   }
   
   /** This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   private void initComponents()//GEN-BEGIN:initComponents
   {
      java.awt.GridBagConstraints gridBagConstraints;
      javax.swing.JLabel jLabel1;
      javax.swing.JLabel jLabel2;
      javax.swing.JLabel jLabel3;
      javax.swing.JLabel jLabel4;

      jLabel1 = new javax.swing.JLabel();
      jLabel4 = new javax.swing.JLabel();
      schemeComboBox = new javax.swing.JComboBox();
      jLabel2 = new javax.swing.JLabel();
      userTextField = new javax.swing.JTextField();
      jLabel3 = new javax.swing.JLabel();
      passwordTextField = new javax.swing.JPasswordField();

      setLayout(new java.awt.GridBagLayout());

      jLabel1.setText("Authentification required for access to root daemon");
      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
      add(jLabel1, gridBagConstraints);

      jLabel4.setText("Scheme:");
      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
      gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
      add(jLabel4, gridBagConstraints);

      schemeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Anonymous", "UsrPwd" }));
      schemeComboBox.addActionListener(new java.awt.event.ActionListener()
      {
         public void actionPerformed(java.awt.event.ActionEvent evt)
         {
            setEnabled(evt);
         }
      });

      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
      gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
      add(schemeComboBox, gridBagConstraints);

      jLabel2.setText("Username:");
      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
      gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
      add(jLabel2, gridBagConstraints);

      userTextField.setText(System.getProperty("user.name"));
      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
      gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
      add(userTextField, gridBagConstraints);

      jLabel3.setText("Password:");
      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
      gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
      add(jLabel3, gridBagConstraints);

      gridBagConstraints = new java.awt.GridBagConstraints();
      gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
      add(passwordTextField, gridBagConstraints);

   }//GEN-END:initComponents

   private void setEnabled(java.awt.event.ActionEvent evt)//GEN-FIRST:event_setEnabled
   {//GEN-HEADEREND:event_setEnabled
      boolean anon = schemeComboBox.getSelectedIndex() == 0;
      passwordTextField.setEnabled(!anon);
      userTextField.setEnabled(!anon);
   }//GEN-LAST:event_setEnabled
   
   
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JPasswordField passwordTextField;
   private javax.swing.JComboBox schemeComboBox;
   private javax.swing.JTextField userTextField;
   // End of variables declaration//GEN-END:variables
   
}
