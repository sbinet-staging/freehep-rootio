package hep.io.root.daemon.xrootd;

import java.net.InetAddress;

/**
 * A connection may be shared by multiple sessions if they have the equivalent ConnectionDescriptor
 * @author tonyj
 */
class XrootdConnectionDescriptor
{
   private String userName;
   private int port;
   private InetAddress address;

   XrootdConnectionDescriptor(InetAddress address, int port, String userName)
   {
      this.address = address;
      this.port = port;
      this.userName = userName.intern();
   }

    public boolean equals(Object obj) 
    {
      if (obj instanceof XrootdConnectionDescriptor)
      {
         XrootdConnectionDescriptor that = (XrootdConnectionDescriptor) obj;
         return this.address.equals(that.address) && 
                this.port == that.port && 
                this.userName == that.userName;
      }
      else return false;
    }

    public int hashCode() 
    {
      return address.hashCode() + port + userName.hashCode();
    }
}