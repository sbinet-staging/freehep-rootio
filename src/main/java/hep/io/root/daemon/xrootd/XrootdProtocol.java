

package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.DaemonInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XrootdProtocol
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
   
   private int bufferSize = 8096;
   private static int MAXGETSIZE = -1;
   
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
   
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   
   /**
    * @param args the command line arguments
    */
   public XrootdProtocol(String host, int port) throws IOException
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
   }
   
   public int getBufferSize()
   {
      return this.bufferSize;
   }
   
   public void setBufferSize(int bufferSize)
   {
      this.bufferSize = bufferSize;
   }
   public static void main(String[] args) throws IOException
   {
      XrootdProtocol xrootd = new XrootdProtocol("glast01.slac.stanford.edu",1094);
      xrootd.login("tonyj");
      xrootd.ping();
      String dir = "/nfs/farm/g/glast/u07/mcenery/systests/GlastRelease/v6r2p1/VerticalGamma100MeV/linux/";
      List files = xrootd.dirList(dir);
      for (Iterator i = files.iterator(); i.hasNext();)
      {
         System.out.println(i.next());
      }
      System.out.println(Arrays.asList(xrootd.stat(dir+"VerticalGamma100MeV_Histos.root")));
      int fh = xrootd.open(dir+"VerticalGamma100MeV_Histos.root",0,kXR_open_read);
      System.out.println("fh="+fh);
      byte[] result = new byte[300000];
      int l = xrootd.read(fh,result,1024);
      System.out.println("l="+result.length);
      xrootd.close(fh);
      
      InputStream in = xrootd.openStream(dir+"VerticalGamma100MeV_Histos.root",0,kXR_open_read);
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
   public List dirList(String path) throws IOException
   {
      message.send(kXR_dirlist,null,path);
      List result = new ArrayList();
      
      for (;;)
      {
         response.read();
         int rlen = response.getLength();
         DataInputStream in = response.getInputStream();
         
         byte[] data = new byte[rlen];
         in.readFully(data);
         String files = new String(data);

         int pos = 0;
         for (int i=0; i<files.length(); i++)
         {
            char c = files.charAt(i);
            if (c =='\n' || c == '\0' )
            {
               result.add(files.substring(pos,i));
               if (c == '\0') break;
               pos = i+1;
            }
         }
         if (response.getStatus() != kXR_oksofar) break;
      }
      
      return result;
   }
   public void login(String username) throws IOException
   {
      bos.reset();
      out.writeInt(12345);
      byte[] user = username.getBytes();
      for (int i=0; i<8; i++) out.writeByte(i<user.length ? user[i] : 0);
      out.writeByte(0);
      out.writeByte(0);
      out.writeByte(0);
      out.writeByte(kXR_useruser);
      out.flush();
      message.send(kXR_login,bos.toByteArray());
      response.read();
   }
   public void ping() throws IOException
   {
      message.send(kXR_ping);
      response.read();
   }
   public String[] stat(String path) throws IOException
   {
      message.send(kXR_stat,null,path);
      response.read();
      int rlen = response.getLength();
      byte[] data = new byte[rlen];
      response.getInputStream().readFully(data);
      return new String(data,0,rlen-1).split(" +");
   }
   public int open(String path, int mode, int options) throws IOException
   {
      bos.reset();
      out.writeShort(mode);
      out.writeShort(options);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      message.send(kXR_open,bos.toByteArray(),path);
      response.read();
      int rlen = response.getLength();
      int handle = response.getInputStream().readInt();
      for (int i=4; i<rlen; i++) response.getInputStream().readByte();
      return handle;
   }
   public void close(int handle) throws IOException
   {
      bos.reset();
      out.writeInt(handle);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      message.send(kXR_close,bos.toByteArray());
      response.read();
   }
   public int read(int handle, byte[] buffer, long fileOffset) throws IOException
   {
      return read(handle,buffer,fileOffset,0,buffer.length);
   }
   public int read(int handle, byte[] buffer, long fileOffset, int bufOffset, int size) throws IOException
   {
      bos.reset();
      out.writeInt(handle);
      out.writeLong(fileOffset);
      out.writeInt(size);
      out.flush();
      message.send(kXR_read,bos.toByteArray());
      int l = 0;
      for (;;)
      {
         response.read();
         int dlen = response.getLength();
         response.getInputStream().readFully(buffer,bufOffset+l,dlen);
         l += dlen;
         if (response.getStatus() != kXR_oksofar) break;
      }
      return l;
   }
   public DaemonInputStream openStream(String path, int mode, int options) throws IOException
   {
      int fh = open(path,mode,options);
      return new RootStream(fh);
   }
   
   private static class Message
   {
      private OutputStream data;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bos);
      int c1 = '9';
      int c2 = '9';
      
      Message(OutputStream data)
      {
         this.data = data;
         
      }
      void send(int message) throws IOException
      {
         send(message,null);
      }
      void send(int message, byte[] extra) throws IOException
      {
         send(message,extra,null);
      }
      
      void send(int message, byte[] extra, String string) throws IOException
      {
         logger.fine("->"+message);
         bos.reset();
         out.writeByte(c1);
         out.writeByte(c2);
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
   private static class Response
   {
      private DataInputStream in;
      private int c1, c2;
      private int status;
      private int dataLength;
      private byte[] data;
      Response(DataInputStream in)
      {
         this.in = in;
      }
      int read() throws IOException
      {
         c1 = in.readUnsignedByte();
         c2 = in.readUnsignedByte();
         status = in.readUnsignedShort();
         dataLength = in.readInt();
         if (status == kXR_error)
         {
            int rc = in.readInt();
            byte[] message = new byte[dataLength-4];
            in.readFully(message);
            throw new IOException("Xrootd error "+rc+": "+new String(message,0,message.length-1));
         }
         else if (status != kXR_ok && status != kXR_oksofar)
         {
            throw new IOException("Xrootd: Unimplemented status recieved: "+status);
         }
         logger.fine("<-"+c1+" "+c2+" "+status+" "+dataLength);
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
      DataInputStream getInputStream()
      {
         return in;
      }
   }
   private class RootStream extends DaemonInputStream
   {
      private byte[] buffer = new byte[bufferSize];
      private int bpos = 0;
      private int blen = 0;
      private int fh;
      
      RootStream(int fh)
      {
         this.fh = fh;
      }
      public int read() throws IOException
      {
         if (bpos >= blen)
         {
            if (!fillBuffer()) return -1;
         }
         int i = buffer[bpos++];
         if (i < 0) i += 256;
         return i;
      }
      
      public void close() throws IOException
      {
         XrootdProtocol.this.close(fh);
      }
      
      public int read(byte[] values, int offset, int size) throws IOException
      {
         if (bpos >= blen)
         {
            long position = this.position+bpos;
            int n = size;
            if (MAXGETSIZE > 0 && n > MAXGETSIZE) n = MAXGETSIZE;
            int l = XrootdProtocol.this.read(fh,values,position,offset,n);
            if (l > 0) this.position += l;
            else l = -1;
            return l;
         }
         else
         {
            int l = Math.min(size,blen-bpos);
            System.arraycopy(buffer, bpos, values, offset, l);
            bpos += l;
            return l;
         }
      }
      
      public long skip(long skip) throws IOException
      {
         setPosition(getPosition()+skip);
         return skip;
      }
      
      public void setPosition(long pos)
      {
         if (pos>position && pos<position+blen)
         {
            bpos = (int) (pos-position);
         }
         else
         {
            blen = 0;
            bpos = 0;
            super.setPosition(pos);
         }
      }
      
      public int available() throws IOException
      {
         return blen - bpos;
      }
      
      private boolean fillBuffer() throws IOException
      {
         position += bpos;
         bpos = 0;
         int n = buffer.length;
         if (MAXGETSIZE > 0 && n > MAXGETSIZE) n = MAXGETSIZE;
         blen = XrootdProtocol.this.read(fh,buffer,position,0,n);
         return true;
      }
      
      public long getPosition()
      {
         return position + bpos;
      }
   }
}
