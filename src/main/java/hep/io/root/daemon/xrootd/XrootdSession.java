package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.DaemonInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A handle is associated with each session, so that they can multiplex on a single
 * open socket to the server.
 * @author tonyj
 */
class XrootdSession
{
   private String userName;
   private Short handle;
   private XrootdMultiplexor multiplexor;
   private int bufferSize = 32768;
   private Object result; // TODO: Ugly, do something better
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private IOException exception = null;
   private static Map/*<XrootdConnectionDescriptor,XrootdMultiplexor>*/ connectionMap = new HashMap/*<XrootdConnectionDescriptor,XrootdMultiplexor>*/();
   private static Random random = new Random();
   /**
    * Many sessions can share a single XrootProtocol
    */
   public XrootdSession(String host, int port, String userName) throws IOException
   {
      this.userName = userName;
      XrootdMultiplexor multiplexor = connectTo(host,port,userName);
      this.multiplexor = multiplexor;
      this.handle = multiplexor.allocateHandle();
   }
   /**
    * Open a host and connect to it without any side effects
    */
   private XrootdMultiplexor connectTo(String host, int port, String userName) throws UnknownHostException, IOException
   {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      // Randomize which host to use if multiple available.
      int offset = random.nextInt(addresses.length);
      for (int i = 0; i<addresses.length; )
      {
         try
         {
            InetAddress address = addresses[(i+offset)%addresses.length];
            XrootdConnectionDescriptor desc = new XrootdConnectionDescriptor(address,port,userName);
            XrootdMultiplexor multiplexor = (XrootdMultiplexor) connectionMap.get(address);
            if (multiplexor == null)
            {
               multiplexor = new XrootdMultiplexor(address,port,userName);
               connectionMap.put(desc,multiplexor);
            }
            return multiplexor;
         }
         catch (IOException x)
         {
            if (++i < addresses.length) continue;
            throw x;
         }
      }
      // We only get here if the host exists but has no addresses, probably not possible?
      throw new UnknownHostException("Host "+host+" has no known inet addresses");
   }
   void redirectConnection(ResponseHandler handler, String host, int port) throws IOException
   {
      XrootdMultiplexor multiplexor;
      try
      {
         multiplexor = connectTo(host,port,userName);
      }
      catch (Exception x)
      {
         handler.handleError(new IOException("Error during redirect",x));
         return;
      }
      
      multiplexor.deregisterResponseHandler(handle);
      close();
      this.multiplexor = multiplexor;
      this.handle = multiplexor.allocateHandle();
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
   }
   
   synchronized void close() throws IOException
   {
      if (handle != null)
      {
         multiplexor.freeHandle(handle);
         handle = null;
      }
   }
   synchronized List dirList(final String path) throws IOException
   {
      final List result = new ArrayList();
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(XrootdMultiplexor.Response response) throws IOException
         {
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
            if (response.getStatus() != XrootdProtocol.kXR_oksofar)
            {
               responseComplete();
            }
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_dirlist,null,path);
         }
      };
      
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse();
      return result;
   }
   
   synchronized void ping() throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(XrootdMultiplexor.Response response)
         {
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_ping);
         }
      };
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse();
   }
   synchronized String[] stat(final String path) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(XrootdMultiplexor.Response response) throws IOException
         {
            int rlen = response.getLength();
            byte[] data = new byte[rlen];
            response.getInputStream().readFully(data);
            result = new String(data,0,rlen-1).split(" +");
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_stat,null,path);
         }
      };
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse();
      return (String[]) result;
   }
   synchronized int open(final String path, final int mode, final int options) throws IOException
   {      
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(XrootdMultiplexor.Response response) throws IOException
         {
            int rlen = response.getLength();
            result = new Integer(response.getInputStream().readInt());
            for (int i=4; i<rlen; i++) response.getInputStream().readByte();
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_open,bos.toByteArray(),path);
         }
      };
      multiplexor.registerResponseHandler(handle,handler);
      bos.reset();
      out.writeShort(mode);
      out.writeShort(options);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      handler.sendMessage();
      waitForResponse();
      return ((Number) result).intValue();
   }
   synchronized void close(int fileHandle) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(XrootdMultiplexor.Response response)
         {
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_close,bos.toByteArray());
         }
      };
      multiplexor.registerResponseHandler(handle,handler);
      bos.reset();
      out.writeInt(fileHandle);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      handler.sendMessage();
      waitForResponse();
   }
   synchronized int read(int fileHandle, byte[] buffer, long fileOffset) throws IOException
   {
      return read(fileHandle,buffer,fileOffset,0,buffer.length);
   }
   synchronized int read(int fileHandle, final byte[] buffer, long fileOffset, final int bufOffset, final int size) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         private int l = 0;
         void handleResponse(XrootdMultiplexor.Response response) throws IOException
         {
            int dlen = response.getLength();
            response.getInputStream().readFully(buffer,bufOffset+l,dlen);
            l += dlen;
            
            if (response.getStatus() != XrootdProtocol.kXR_oksofar)
            {
               result = new Integer(l);
               responseComplete();
            }
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_read,bos.toByteArray());            
         }
      };
      
      multiplexor.registerResponseHandler(handle,handler);
      bos.reset();
      out.writeInt(fileHandle);
      out.writeLong(fileOffset);
      out.writeInt(size);
      out.flush();
      handler.sendMessage();
      waitForResponse();
      return ((Number) result).intValue();
   }
   
   DaemonInputStream openStream(String path, int mode, int options) throws IOException
   {
      int fh = open(path,mode,options);
      return new XrootdInputStream(this,fh,bufferSize);
   }
   
   private synchronized void responseComplete()
   {
      multiplexor.deregisterResponseHandler(handle);
      exception = null;
      XrootdSession.this.notify();
   }
   synchronized void responseComplete(IOException x)
   {
      multiplexor.deregisterResponseHandler(handle);
      exception = x;
      notify();
   }
   private void waitForResponse() throws IOException
   {
      try
      {
         wait();
         if (exception != null) throw exception;
      }
      catch (InterruptedException x)
      {
         IOException io = new InterruptedIOException("Xrootd IO interrupted");
         io.initCause(x);
         throw io;
      }
   }
   
   /**
    * Getter for property bufferSize.
    * @return Value of property bufferSize.
    */
   public int getBufferSize()
   {
      return this.bufferSize;
   }
   
   /**
    * Setter for property bufferSize.
    * @param bufferSize New value of property bufferSize.
    */
   public void setBufferSize(int bufferSize)
   {
      this.bufferSize = bufferSize;
   }
   
   protected void finalize() throws Throwable
   {
      close();
      super.finalize();
   }
}