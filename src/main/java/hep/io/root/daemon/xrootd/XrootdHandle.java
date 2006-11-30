package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.DaemonInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tonyj
 */
class XrootdHandle
{
   private String userName;
   private Short handle;
   private XrootdProtocol protocol;
   private int bufferSize = 32768;
   private Object result; // TODO: Ugly, do something better
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private static Map/*<XrootdConnectionDescriptor,XrootdProtocol>*/ connectionMap = new HashMap/*<XrootdConnectionDescriptor,XrootdProtocol>*/();
   /**
    * Many handles can share a single XrootProtocol
    */
   public XrootdHandle(String host, int port, String userName) throws IOException
   {
      this.userName = userName;
      connectTo(host,port,userName);
   }
   private void connectTo(String host, int port, String userName) throws IOException
   {
      if (handle != null) close();
      XrootdConnectionDescriptor desc = new XrootdConnectionDescriptor(host,port,userName);
      protocol = (XrootdProtocol) connectionMap.get(desc);
      if (protocol == null)
      {
         protocol = new XrootdProtocol(host,port,userName);
         connectionMap.put(desc,protocol);
      }
      handle = protocol.allocateHandle();
   }
   synchronized void close() throws IOException
   {
      if (handle != null)
      {
         protocol.freeHandle(handle);
         handle = null;
      }
   }
   synchronized List dirList(final String path) throws IOException
   {
      final List result = new ArrayList();
      ResponseHandler handler = new ResponseHandler()
      {
         public void handleRedirect(String host, int port)
         {
            throw new RuntimeException("Unhandled redirect");
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,XrootdProtocol.kXR_dirlist,null,path);
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            try
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
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
      };
      
      protocol.registerResponseHandler(handle,handler);
      protocol.sendMessage(handle,XrootdProtocol.kXR_dirlist,null,path);
      waitForResponse();
      return result;
   }
   
   synchronized void ping() throws IOException
   {
      ResponseHandler handler = new ResponseHandler()
      {
         public void handleRedirect(String host, int port)
         {
            throw new RuntimeException("Unhandled redirect");
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,XrootdProtocol.kXR_ping);
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            responseComplete();
         }
      };
      protocol.registerResponseHandler(handle,handler);
      protocol.sendMessage(handle,XrootdProtocol.kXR_ping);
      waitForResponse();
   }
   synchronized String[] stat(final String path) throws IOException
   {
      ResponseHandler handler = new ResponseHandler()
      {
         public void handleRedirect(String host, int port)
         {
            try
            {
               protocol.deregisterResponseHandler(handle);
               connectTo(host,port,userName);
               protocol.registerResponseHandler(handle,this);
               protocol.sendMessage(handle,protocol.kXR_stat,null,path);
            }
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,protocol.kXR_stat,null,path);
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            try
            {
               int rlen = response.getLength();
               byte[] data = new byte[rlen];
               response.getInputStream().readFully(data);
               result = new String(data,0,rlen-1).split(" +");
               responseComplete();
            }
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
      };
      protocol.registerResponseHandler(handle,handler);
      protocol.sendMessage(handle,protocol.kXR_stat,null,path);
      waitForResponse();
      return (String[]) result;
   }
   synchronized int open(final String path, final int mode, final int options) throws IOException
   {
      ResponseHandler handler = new ResponseHandler()
      {
         public void handleRedirect(String host, int port)
         {
            try
            {
               protocol.deregisterResponseHandler(handle);
               connectTo(host,port,userName);
               protocol.registerResponseHandler(handle,this);
               protocol.sendMessage(handle,protocol.kXR_open,bos.toByteArray(),path);
            }
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,protocol.kXR_open,bos.toByteArray(),path);
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            try
            {
               int rlen = response.getLength();
               result = new Integer(response.getInputStream().readInt());
               for (int i=4; i<rlen; i++) response.getInputStream().readByte();
               responseComplete();
            }
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
      };
      protocol.registerResponseHandler(handle,handler);
      
      bos.reset();
      out.writeShort(mode);
      out.writeShort(options);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      protocol.sendMessage(handle,protocol.kXR_open,bos.toByteArray(),path);
      waitForResponse();
      return ((Number) result).intValue();
   }
   synchronized void close(int fileHandle) throws IOException
   {
      ResponseHandler handler = new ResponseHandler()
      {
         public void handleRedirect(String host, int port)
         {
            throw new RuntimeException("Unhandled redirect");
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,protocol.kXR_close,bos.toByteArray());
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            responseComplete();
         }
      };
      protocol.registerResponseHandler(handle,handler);
      bos.reset();
      out.writeInt(fileHandle);
      for (int i=0; i<12; i++) out.writeByte(0);
      out.flush();
      protocol.sendMessage(handle,protocol.kXR_close,bos.toByteArray());
      waitForResponse();
   }
   synchronized int read(int fileHandle, byte[] buffer, long fileOffset) throws IOException
   {
      return read(fileHandle,buffer,fileOffset,0,buffer.length);
   }
   synchronized int read(int fileHandle, final byte[] buffer, long fileOffset, final int bufOffset, final int size) throws IOException
   {
      ResponseHandler handler = new ResponseHandler()
      {
         private int l = 0;
         public void handleRedirect(String host, int port)
         {
            throw new RuntimeException("Unhandled redirect");
         }
         public void handleWait(int seconds)
         {
            try
            {
               Thread.sleep(1000); // TODO: This blocks all xrootd event processing!
               protocol.sendMessage(handle,protocol.kXR_read,bos.toByteArray());
            }
            catch (Exception x)
            {
               x.printStackTrace();
            }
         }
         public void handleResponse(XrootdProtocol.Response response)
         {
            try
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
            catch (IOException x)
            {
               // Fixme: Handle IO Exception
               x.printStackTrace();
            }
         }
      };
      
      protocol.registerResponseHandler(handle,handler);
      bos.reset();
      out.writeInt(fileHandle);
      out.writeLong(fileOffset);
      out.writeInt(size);
      out.flush();
      protocol.sendMessage(handle,protocol.kXR_read,bos.toByteArray());
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
      protocol.deregisterResponseHandler(handle);
      XrootdHandle.this.notify();
   }
   private void waitForResponse() throws IOException
   {
      try
      {
         wait();
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