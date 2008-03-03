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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A handle is associated with each session, so that they can multiplex on a single
 * open socket to the server.
 * @author tonyj
 */
class Session
{
   private static final int WAIT_TIMEOUT = Integer.getInteger("hep.io.root.deamon.xrootd.timeout", 3000).intValue();
   private static final int WAIT_LIMIT = Integer.getInteger("hep.io.root.deamon.xrootd.waitLimit", 1000).intValue();
   private String userName;
   private Short handle;
   private Multiplexor multiplexor;
   private int bufferSize = 32768;
   private Object result; // TODO: Ugly, do something better
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private IOException exception = null;
   private boolean flag;
   private String initialHost;
   private int initialPort;
   private static Logger logger = Logger.getLogger("hep.io.root.daemon.xrootd");
   
   /**
    * Many sessions can share a single XrootProtocol
    */
   public Session(String host, int port, String userName) throws IOException
   {
      this.initialHost = host;
      this.initialPort = port;
      this.userName = userName;
      Multiplexor newMultiplexor = connectTo(host,port,userName);
      this.multiplexor = newMultiplexor;
      this.handle = newMultiplexor.allocate(this);
   }

   /**
    * Open a host and connect to it without any side effects
    */
   private Multiplexor connectTo(String host, int port, String userName) throws UnknownHostException, IOException
   {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      // Randomize which host to use if multiple available.
      Collections.shuffle(Arrays.asList(addresses));
      
      for (int i = 0; i<addresses.length; )
      {
         try
         {
            InetAddress address = addresses[i];
            ConnectionDescriptor desc = new ConnectionDescriptor(address,port,userName);
            return Multiplexor.allocate(desc);
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

   void redirectToInitial(ResponseHandler handler) throws IOException
   {
       redirectConnection(handler,initialHost,initialPort);
   }
   
   void redirectConnection(ResponseHandler handler, String host, int port) throws IOException
   {
      Multiplexor newMultiplexor;
      try
      {
         newMultiplexor = connectTo(host,port,userName);
      }
      catch (Exception x)
      {
         IOException iox = new IOException("Error during redirect");
         iox.initCause(x);
         handler.handleError(iox);
         return;
      }
      
      this.multiplexor.deregisterResponseHandler(handle);
      close();
      this.multiplexor = newMultiplexor;
      this.handle = newMultiplexor.allocate(this);
      newMultiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
   }
   Short getHandle()
   {
      return handle;
   }
   
   synchronized void close() throws IOException
   {
      if (multiplexor != null)
      {
         multiplexor.free(this);
         multiplexor = null;
         handle = null;
      }
   }
   synchronized List dirList(final String path) throws IOException
   {
      final List dirListResult = new ArrayList();
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(Multiplexor.Response response) throws IOException
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
                  dirListResult.add(files.substring(pos,i));
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
      waitForResponse("dirList");
      return dirListResult;
   }
   
   synchronized void ping() throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_ping);
         }
      };
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse("ping");
   }
   synchronized String[] stat(final String path) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(Multiplexor.Response response) throws IOException
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
      waitForResponse("stat");
      return (String[]) result;
   }
   synchronized String query(final int queryType, final String path) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(Multiplexor.Response response) throws IOException
         {
            int rlen = response.getLength();
            byte[] data = new byte[rlen];
            response.getInputStream().readFully(data);
            result = new String(data,0,rlen);
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_query,bos.toByteArray(),path);
         }
      };
      bos.reset();
      out.writeShort(queryType);
      for (int i=0; i<14; i++) out.writeByte(0);
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse("query");
      return (String) result;
   }
   synchronized String prepare(String[] path, int options, int priority) throws IOException
   {
      final StringBuffer plist = new StringBuffer();
      for (int i=0; i<path.length; i++)
      {
         plist.append(path[i]);
         plist.append('\n');
      }
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(Multiplexor.Response response) throws IOException
         {
            int rlen = response.getLength();
            byte[] data = new byte[rlen];
            response.getInputStream().readFully(data);
            result = new String(data,0,rlen-1);
            responseComplete();
         }
         void sendMessage() throws IOException
         {
            multiplexor.sendMessage(handle,XrootdProtocol.kXR_prepare,bos.toByteArray(),plist.toString());
         }
      };
      bos.reset();
      out.writeByte(options);
      out.writeByte(priority);
      for (int i=0; i<14; i++) out.writeByte(0);
      out.flush();
      
      multiplexor.registerResponseHandler(handle,handler);
      handler.sendMessage();
      waitForResponse("prepare");
      return (String) result;
   }
   synchronized int open(final String path, final int mode, final int options) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
         void handleResponse(Multiplexor.Response response) throws IOException
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
      waitForResponse("open");
      return ((Number) result).intValue();
   }
   synchronized void close(int fileHandle) throws IOException
   {
      ResponseHandler handler = new ResponseHandler(this)
      {
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
      waitForResponse("close");
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
         void handleResponse(Multiplexor.Response response) throws IOException
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
      waitForResponse("read");
      return ((Number) result).intValue();
   }
   
   DaemonInputStream openStream(String path, int mode, int options) throws IOException
   {
      int fh = open(path,mode,options);
      return new XrootdInputStream(this,fh,bufferSize);
   }
   
   synchronized void responseComplete()
   {
      responseComplete(null);
   }
   synchronized void responseComplete(IOException x)
   {
      multiplexor.deregisterResponseHandler(handle);
      exception = x;
      flag = true;
      notify();
   }
   private void waitForResponse(String reason) throws IOException
   {
      try
      {
         flag = false;
         for (int i=0; !flag;)
         {
           wait(WAIT_TIMEOUT);
           if (!flag)
           {
               i+=WAIT_TIMEOUT;
               logger.warning("Waiting for response for "+(i/1000)+" secs " + reason + multiplexor);
               if (i>=WAIT_LIMIT*WAIT_TIMEOUT) throw new IOException("Timeout waiting for response after "+(i/1000)+"secs");   
           }
         }
         if (exception != null)
         {
            IOException io = new IOException(exception.getMessage());
            // preserve original exception since it occured on another thread
            // otherwise loose useful stacktrace info.
            io.initCause(exception);
            throw io;
         }
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