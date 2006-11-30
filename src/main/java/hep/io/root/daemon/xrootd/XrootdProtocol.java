package hep.io.root.daemon.xrootd;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

class XrootdProtocol implements Runnable
{
   public final static int defaultPort = 1094;
   
   final static int kXR_DataServer = 1;
   final static int kXR_LBalServer = 0;
   final static int kXR_maxReqRetry = 10;
   
   final static int  kXR_auth    =  3000;
   final static int  kXR_query   =  3001;
   final static int  kXR_chmod   =  3002;
   final static int  kXR_close   =  3003;
   final static int  kXR_dirlist =  3004;
   final static int  kXR_getfile =  3005;
   final static int  kXR_protocol=  3006;
   final static int  kXR_login   =  3007;
   final static int  kXR_mkdir   =  3008;
   final static int  kXR_mv      =  3009;
   final static int  kXR_open    =  3010;
   final static int  kXR_ping    =  3011;
   final static int  kXR_putfile =  3012;
   final static int  kXR_read    =  3013;
   final static int  kXR_rm      =  3014;
   final static int  kXR_rmdir   =  3015;
   final static int  kXR_sync    =  3016;
   final static int  kXR_stat    =  3017;
   final static int  kXR_set     =  3018;
   final static int  kXR_write   =  3019;
   final static int  kXR_admin   =  3020;
   final static int  kXR_prepare =  3021;
   final static int  kXR_statx   =  3022;
   
   final static int  kXR_ok       = 0;
   final static int  kXR_oksofar  = 4000;
   final static int  kXR_attn     = 4001;
   final static int  kXR_authmore = 4002;
   final static int  kXR_error    = 4003;
   final static int  kXR_redirect = 4004;
   final static int  kXR_wait     = 4005;
   
   public final static int kXR_ur = 0x100;
   public final static int kXR_uw = 0x080;
   public final static int kXR_ux = 0x040;
   public final static int kXR_gr = 0x020;
   public final static int kXR_gw = 0x010;
   public final static int kXR_gx = 0x008;
   public final static int kXR_or = 0x004;
   public final static int kXR_ow = 0x002;
   public final static int kXR_ox = 0x001;
   
   public final static int kXR_compress = 1;
   public final static int kXR_delete   = 2;
   public final static int kXR_force    = 4;
   public final static int kXR_new      = 8;
   public final static int kXR_open_read= 16;
   public final static int kXR_open_updt= 32;
   public final static int kXR_async    = 64;
   public final static int kXR_refresh  = 128;
   
   final static int kXR_useruser = 0;
   final static int kXR_useradmin = 1;
   
   private static Logger logger = Logger.getLogger("hep.io.root.daemon");
   static
   {
      if (System.getProperty("debugRootDaemon")!= null)
      {
         logger.setLevel(Level.FINE);
         ConsoleHandler handler = new ConsoleHandler();
         handler.setLevel(Level.FINE);
         logger.addHandler(handler);
      }
   }
   private Socket socket;
   private Message message;
   private Response response;
   private Map/* <Short,ResponseHandler> */ responseMap = new HashMap /*<Short,ResponseHandler>*/ ();
   
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private BitSet handles = new BitSet();
   
   /**
    * Low level implementation of the Xrootd protocol.
    * One XrootdProtocol object may be shared by any number of clients, who
    * may call any of the methods simultaneously on different threads.
    * @param host The host to connect to
    * @param port The port to connect to
    */
   XrootdProtocol(String host, int port, String userName) throws IOException
   {
      if (port == -1) port = defaultPort;
      Socket s = new Socket(host,port);
      
      bos.reset();
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(0);
      out.writeInt(4);
      out.writeInt(2012);
      out.flush();
      bos.writeTo(s.getOutputStream());
      
      DataInputStream in = new DataInputStream(s.getInputStream());
      response = new Response(in);
      response.read();
      int protocol = response.getInputStream().readInt();
      int mode = response.getInputStream().readInt();
      
      message = new Message(s.getOutputStream());
      
      bos.reset();
      out.writeInt(12345);
      byte[] user = userName.getBytes();
      for (int i=0; i<8; i++) out.writeByte(i<user.length ? user[i] : 0);
      out.writeByte(0);
      out.writeByte(0);
      out.writeByte(0);
      out.writeByte(kXR_useruser);
      out.flush();
      sendMessage(new Short((short)0),kXR_login,bos.toByteArray());
      response.read();
      
      // Start a thread which will listen for future responses
      // TODO: It would be better to use a single thread listening on all 
      // open sockets
      Thread t = new Thread(this,"XrootdReader-"+host+":"+port);
      t.setDaemon(true);
      t.start();
   }
   public void run()
   {
      for (;;)
      {
         try
         {
            response.read();
            int status = response.getStatus();
            
            if (status == kXR_wait)
            {
               DataInputStream in = response.getInputStream();
               int seconds = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               System.out.println("Xrootd wait: "+new String(message,0,message.length)+" seconds="+seconds);
               Short handle = response.getHandle();
               ResponseHandler handler;
               synchronized (responseMap)
               {
                  handler = (ResponseHandler) responseMap.get(handle);
               }
               // TODO: What if handler == null?
               handler.handleWait(seconds);
            }
            else if (status == kXR_redirect)
            {
               DataInputStream in = response.getInputStream();
               int port = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               String host = new String(message,0,message.length);
               Short handle = response.getHandle();
               ResponseHandler handler;
               synchronized (responseMap)
               {
                  handler = (ResponseHandler) responseMap.get(handle);
               }
               // TODO: What if handler == null?
               handler.handleRedirect(host,port);
            }
            else if (status == kXR_ok || status == kXR_oksofar)
            {
               Short handle = response.getHandle();
               ResponseHandler handler;
               synchronized (responseMap)
               {
                  handler = (ResponseHandler) responseMap.get(handle);
               }
               // TODO: What if handler == null?
               handler.handleResponse(response);
            }
            else
            {
               throw new IOException("Xrootd: Unimplemented status received: "+status);
            }
         }
         catch (IOException x)
         {
            // TODO: we need to report errors to anyone waiting for a response
            // and die.
            x.printStackTrace();
         }
      }
   }
   Short allocateHandle()
   {
      synchronized (handles)
      {
         int result = handles.nextClearBit(0);
         handles.set(result);
         return new Short((short) result);
      }
   }
   void freeHandle(Short handle)
   {
      synchronized (handles)
      {
         handles.clear(handle.intValue());
      }
   }
   void registerResponseHandler(Short handle, ResponseHandler handler)
   {
      synchronized (responseMap)
      {
         responseMap.put(handle,handler);
      }
   }
   void deregisterResponseHandler(Short handle)
   {
      synchronized (responseMap)
      {
         responseMap.remove(handle);
      }
   }
   void sendMessage(Short handle, int message) throws IOException
   {
      sendMessage(handle,message,null);
   }
   void sendMessage(Short handle, int message, byte[] extra) throws IOException
   {
      sendMessage(handle,message,extra,null);
   }
   void sendMessage(Short handle,int message, byte[] extra, String string) throws IOException
   {
      this.message.send(handle,message,extra,string);
   }
   
   public static void main(String[] args) throws IOException
   {
      XrootdHandle handle = new XrootdHandle("glast01.slac.stanford.edu",1094,"tonyj");
      handle.ping();
      String dir = "/nfs/farm/g/glast/u07/mcenery/systests/GlastRelease/v6r2p1/VerticalGamma100MeV/linux/";
      List files = handle.dirList(dir);
      for (Iterator i = files.iterator(); i.hasNext();)
      {
         System.out.println(i.next());
      }
      System.out.println(Arrays.asList(handle.stat(dir+"VerticalGamma100MeV_Histos.root")));
      int fh = handle.open(dir+"VerticalGamma100MeV_Histos.root",0,kXR_open_read);
      System.out.println("fh="+fh);
      byte[] result = new byte[300000];
      int l = handle.read(fh,result,1024);
      System.out.println("l="+result.length);
      handle.close(fh);
      
      InputStream in = handle.openStream(dir+"VerticalGamma100MeV_Histos.root",0,kXR_open_read);
      int p = 0;
      for (;;)
      {
         int ll = in.read(result);
         if (ll < 0) break;
         p += ll;
      }
      in.close();
      System.out.println("Read "+p+" bytes");
      
   }
   
   private static class Message
   {
      private OutputStream data;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bos);
      
      Message(OutputStream data)
      {
         this.data = data;
         
      }
      
      synchronized void send(Short handle, int message, byte[] extra, String string) throws IOException
      {
         logger.fine("->"+message);
         bos.reset();
         out.writeShort(handle.shortValue());
         out.writeShort(message);
         for (int i=0; i<16; i++) out.writeByte(extra == null ? 0 : extra[i]);
         if (string == null)
         {
            out.writeInt(0);
         }
         else
         {
            byte[] bytes = string.getBytes();
            out.writeInt(bytes.length);
            out.write(bytes);
         }
         out.flush();
         bos.writeTo(data);
      }
   }
   static class Response
   {
      private DataInputStream in;
      private Short handle;
      private int status;
      private int dataLength;
      private byte[] data;
      private Response(DataInputStream in)
      {
         this.in = in;
      }
      private int read() throws IOException
      {
         handle = new Short(in.readShort());
         status = in.readUnsignedShort();
         dataLength = in.readInt();
         if (status == kXR_error)
         {
            int rc = in.readInt();
            byte[] message = new byte[dataLength-4];
            in.readFully(message);
            throw new IOException("Xrootd error "+rc+": "+new String(message,0,message.length-1));
         }
         logger.fine("<-"+handle+" "+status+" "+dataLength);
         return status;
      }
      int getStatus()
      {
         return status;
      }
      int getLength()
      {
         return dataLength;
      }
      Short getHandle()
      {
         return handle;
      }
      DataInputStream getInputStream()
      {
         return in;
      }
   }
}
