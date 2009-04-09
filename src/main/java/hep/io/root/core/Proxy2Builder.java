package hep.io.root.core;


/**
 * @author tonyj
 * @version $Id$
 */
class Proxy2Builder extends ProxyBuilder
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
