package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;


/**
 * The core class for dealing with root: protocol connections.
 * Currently only supports reading files. Currently only supports
 * plain text (insecure) authorization.
 * @author Tony Johnson
 */
public class XrootdURLConnection extends URLConnection
{
   private String username;
   private String password;
   private String auth; // authorization mode to use
   private int bufferSize = 0;
   
   private long date;
   private long fSize;
   private int flags;
   private static Logger logger = Logger.getLogger("hep.io.root.daemon.xrootd");
   
   public static final String XROOT_AUTHORIZATION_SCHEME = "scheme";
   public static final String XROOT_AUTHORIZATION_SCHEME_ANONYMOUS = "anonymous";
   public static final String XROOT_AUTHORIZATION_USER = "user";   
   public static final String XROOT_AUTHORIZATION_PASSWORD = "password";   
   public static final String XROOT_BUFFER_SIZE = "bufferSize";

   private Session session;
   private int openStreamCount;

   XrootdURLConnection(URL url)
   {
      super(url);
   }
   public InputStream getInputStream() throws IOException
   {
      connect();
      InputStream stream = session.openStream(url.getFile(),0,XrootdProtocol.kXR_open_read);
      ((XrootdInputStream) stream).setConnection(this);
      openStreamCount++;
      return stream;
   }
   public void connect() throws IOException
   {
      if (connected) return;

      if (auth == null) auth = System.getProperty("root.scheme");
      if (auth != null && auth.equalsIgnoreCase(XROOT_AUTHORIZATION_SCHEME_ANONYMOUS))
      {
         username = XROOT_AUTHORIZATION_SCHEME_ANONYMOUS;
         try
         {
            password = System.getProperty("user.name")+"@"+InetAddress.getLocalHost().getCanonicalHostName();
         }
         catch (SecurityException x)
         {
            password = "freehep-user@freehep.org";
         }
      }
      
      if (username == null) username = System.getProperty("root.user");
      if (password == null) password = System.getProperty("root.password");
      
      // Check for username password, if not present, and if allowed, prompt the user.
      if ((password == null || username == null) && getAllowUserInteraction())
      {
         int port = url.getPort();
         if (port == -1) port = XrootdProtocol.defaultPort;
         PasswordAuthentication pa = Authenticator.requestPasswordAuthentication(url.getHost(),null,port,"root","Username/Password required", auth);
         if (pa != null) 
         {
            username = pa.getUserName();
            password = new String(pa.getPassword());
         }
      }

      if (password == null || username == null) throw new IOException("Authorization Required");
            
      logger.fine("Opening rootd connection to: "+url);
      session = new Session(url.getHost(),url.getPort(),username);
      try
      {
         if (bufferSize != 0) session.setBufferSize(bufferSize);

         // ToDo: This could be delayed until needed.
         String[] fstat = session.stat(url.getFile());
         fSize = Long.parseLong(fstat[1]);
         flags = Integer.parseInt(fstat[2]);
         date = Long.parseLong(fstat[3])*1000;
         connected = true;
      }
      catch (IOException t)
      {
         disconnect();
         throw t;
      }
   }
   public void disconnect() throws IOException
   {
      if (session != null)
      {
         session.close();
         session = null;
      }
      connected = false;
   }
   
   public int getContentLength()
   {
      if (session == null) return -1;
      return (int) fSize;
   }
   
   public long getLastModified()
   {
      if (session == null) return 0;
      return date;
   }
   
   public long getDate()
   {
      return getLastModified();
   }   
   
   public long getCheckSum() throws IOException
   {
      if (session == null) return -1;
      else
      {
         String result = session.query(XrootdProtocol.kXR_Qcksum,url.getFile());
         String[] split = result.split(" ");
         return Long.parseLong(split[1]);
      }
   }
   
   public void setRequestProperty(String key, String value)
   {
      if      (key.equalsIgnoreCase(XROOT_AUTHORIZATION_USER))     username = value;
      else if (key.equalsIgnoreCase(XROOT_AUTHORIZATION_PASSWORD)) password = value;
      else if (key.equalsIgnoreCase(XROOT_AUTHORIZATION_SCHEME ))  auth = value;
      else if (key.equalsIgnoreCase(XROOT_BUFFER_SIZE)) bufferSize = Integer.parseInt(value);
   }  

   void streamClosed() throws IOException
   {
      openStreamCount--;
      if (openStreamCount == 0) disconnect();
   }
        
   protected void finalize() throws Throwable
   {
      disconnect();
      super.finalize();
   }
}
