/*
 * Proxy2Builder.java
 *
 * Created on January 16, 2002, 11:49 AM
 */
package hep.io.root.core;


/**
 * @author tonyj
 * @version $Id$
 */
public class Proxy2Builder extends ProxyBuilder
{
   public Proxy2Builder()
   {
      super(false);
   }

   public String getStem()
   {
      return "hep.io.root.proxy2";
   }
}
