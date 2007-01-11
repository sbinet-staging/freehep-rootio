package hep.io.root.daemon.xrootd;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Timer;

class XrootdMultiplexor implements Runnable
{   
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
   
   private static Timer timer = new Timer("XrootdReader-timer",true);
   
   private Message message;
   private Response response;
   private Map/* <Short,ResponseHandler> */ responseMap = new HashMap /*<Short,ResponseHandler>*/ ();
   
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private BitSet handles = new BitSet();
   
   /**
    * Low level implementation of the Xrootd multiplexor protocol.
    * One XrootdMultiplexor object may be shared by any number of clients, who
    * may call any of the methods simultaneously on different threads.
    * @param address The address to connect to
    * @param port The port to connect to
    */
   XrootdMultiplexor(InetAddress address, int port, String userName) throws IOException
   {
      if (port == -1) port = XrootdProtocol.defaultPort;
      Socket s = new Socket(address,port);
      
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
      out.writeByte(XrootdProtocol.kXR_useruser);
      out.flush();
      sendMessage(new Short((short)0),XrootdProtocol.kXR_login,bos.toByteArray());
      response.read();
      
      // Start a thread which will listen for future responses
      // TODO: It would be better to use a single thread listening on all
      // open sockets
      // ToDo: This is never closed, there should be a timeout for inactivity
      Thread t = new Thread(this,"XrootdReader-"+address+":"+port);
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
            Short handle = response.getHandle();
            final ResponseHandler handler;
            synchronized (responseMap)
            {
               handler = (ResponseHandler) responseMap.get(handle);
            }
            
            if (handler == null) throw new IOException("No handler found for handle "+handle);
            if (status == XrootdProtocol.kXR_error)
            {
               DataInputStream in = response.getInputStream();
               int rc = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               handler.handleError(new IOException("Xrootd error "+rc+": "+new String(message,0,message.length-1)));
            }
            else if (status == XrootdProtocol.kXR_wait)
            {
               DataInputStream in = response.getInputStream();
               int seconds = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               System.out.println("Xrootd wait: "+new String(message,0,message.length)+" seconds="+seconds);
               
               TimerTask task = new TimerTask()
               {
                  public void run()
                  {
                     try
                     {
                        handler.sendMessage();
                     }
                     catch (IOException x)
                     {
                        //ToDo: Fixme
                        x.printStackTrace();
                     }
                  }
               };
               timer.schedule(task,1000*seconds);
            }
            else if (status == XrootdProtocol.kXR_redirect)
            {
               DataInputStream in = response.getInputStream();
               int port = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               String host = new String(message,0,message.length);
               handler.handleRedirect(host,port);
            }
            else if (status == XrootdProtocol.kXR_ok || status == XrootdProtocol.kXR_oksofar)
            {
               handler.handleResponse(response);
            }
            else
            {
               throw new IOException("Xrootd: Unimplemented status received: "+status);
            }
         }
         catch (IOException x)
         {
            // We should only get here if there was a real IO error on the socket.
            // ToDo: Something better.
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
      private Response(DataInputStream in)
      {
         this.in = in;
      }
      private int read() throws IOException
      {
         handle = new Short(in.readShort());
         status = in.readUnsignedShort();
         dataLength = in.readInt();
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
