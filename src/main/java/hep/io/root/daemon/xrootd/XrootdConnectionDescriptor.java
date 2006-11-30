package hep.io.root.daemon.xrootd;

/**
 * A connection may be shared by multiple users with the equivalent ConnectionDescriptor
 * @author tonyj
 */
class XrootdConnectionDescriptor
{
   private String host;
   private int port;
   private String userName;

   XrootdConnectionDescriptor(String host, int port, String userName)
   {
      this.host = host.intern();
      this.port = port;
      this.userName = userName.intern();
   }

    public boolean equals(Object obj) 
    {
      if (obj instanceof XrootdConnectionDescriptor)
      {
         XrootdConnectionDescriptor that = (XrootdConnectionDescriptor) obj;
         return this.host == that.host && 
                this.port == that.port && 
                this.userName == that.userName;
      }
      else return false;
    }

    public int hashCode() 
    {
      return host.hashCode() + port + userName.hashCode();
    }
}