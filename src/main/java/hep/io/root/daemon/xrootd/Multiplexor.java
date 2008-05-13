package hep.io.root.daemon.xrootd;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
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

class Multiplexor implements Runnable
{
   private static final int MAX_IDLE = 5000;
   private static Logger logger = Logger.getLogger("hep.io.root.daemon.xrootd");
   
   static
   {
      if (System.getProperty("debugRootDaemon") != null)
      {
         logger.setLevel(Level.FINER);
         ConsoleHandler handler = new ConsoleHandler();
         handler.setLevel(Level.FINER);
         logger.addHandler(handler);
      }
   }
   
   private static Timer timer = new Timer("XrootdReader-timer",true);
   private static Map/*<ConnectionDescriptor,Multiplexor>*/ connectionMap = new HashMap/*<ConnectionDescriptor,Multiplexor>*/();
   
   private boolean socketClosed = false;
   private Socket socket;
   private Thread thread;
   private ConnectionDescriptor descriptor;
   private Message message;
   private Response response;
   private Map/* <Short,ResponseHandler> */ responseMap = new HashMap /*<Short,ResponseHandler>*/ ();
   private TimerTask idleTimer;
   
   private ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
   private DataOutputStream out = new DataOutputStream(bos);
   private BitSet handles = new BitSet();
   
   /**
    * Attempts to assign a multiplexor to a session.
    * This is an atomic operation, guaranteed to either return a valid multiplexor,
    * perhaps already shared with other session, or to throw an exception.
    */
   
   static Multiplexor allocate(ConnectionDescriptor desc) throws IOException
   {
      Multiplexor m;
      synchronized (connectionMap)
      {
         m = (Multiplexor) connectionMap.get(desc);
         if (m == null)
         {
            m = new Multiplexor(desc);
            connectionMap.put(desc,m);
         }
      }
      return m;
   }
   Short allocate(Session session)
   {
      int handle;
      synchronized (handles)
      {
         //ToDo: What if we run out of handles?
         handle = handles.nextClearBit(0);
         handles.set(handle);
         
         if (idleTimer != null)
         {
            idleTimer.cancel();
            idleTimer = null;
         }
      }
      logger.fine(descriptor+" Add session "+handle);
      return new Short((short) handle);
   }
   void free(Session session)
   {
      int handle;
      int nOpen;
      synchronized (handles)
      {
         handle = session.getHandle().intValue();
         handles.clear(handle);
         nOpen = handles.cardinality();
         
         if (nOpen == 0)
         {
            idleTimer = new TimerTask()
            {
               public void run()
               {
                   try
                   {
                      close();
                   }
                   catch (Throwable t)
                   {
                       logger.log(Level.SEVERE,"Unhandled exception while closing multiplexor ",t);
                   }
               }
            };
            timer.schedule(idleTimer,MAX_IDLE);
         }
      }
      logger.fine(descriptor+" Free session "+handle+" nOpen="+nOpen);              
   }
   private void close()
   {
      synchronized (connectionMap)
      {
         synchronized (handles)
         {
            if (handles.cardinality() > 0) return;
         }
         connectionMap.remove(this.descriptor);
      }
      logger.fine(descriptor+" Closing connection");
      
      try
      {
         socketClosed = true;
         socket.close();
         logger.fine(descriptor+" Closed socket");              
      }
      catch (IOException x)
      {
         logger.log(Level.WARNING,descriptor+" Error while closing socket",x);
      }
   }
   
   
   /**
    * Low level implementation of the Xrootd multiplexor protocol.
    * One XrootdMultiplexor object may be shared by any number of clients, who
    * may call any of the methods simultaneously on different threads.
    * @param address The address to connect to
    * @param port The port to connect to
    */
   private Multiplexor(ConnectionDescriptor desc) throws IOException
   {
      logger.fine(desc+" Opening connection");
      this.descriptor = desc;
      int port = desc.getPort();
      if (port == -1) port = XrootdProtocol.defaultPort;
      socket = new Socket(desc.getAddress(),port);
      try
      {
         bos.reset();
         out.writeInt(0);
         out.writeInt(0);
         out.writeInt(0); 
         out.writeInt(4);
         out.writeInt(2012);
         out.flush();
         bos.writeTo(socket.getOutputStream());
         
         DataInputStream in = new DataInputStream(socket.getInputStream());
         int check = in.readInt();
         if (check == 8) throw new IOException("rootd protocol not supported");
         if (check != 0) throw new IOException("Unexpected initial handshake response");
         int rlen = in.readInt();
         if (rlen != 8) throw new IOException("Unexpected initial handshake length");
         int protocol = in.readInt();
         int mode = in.readInt();
         
         logger.fine(desc+" Logging in protocol="+protocol+" mode="+mode);
         
         message = new Message(socket.getOutputStream());
         
         bos.reset();
         out.writeInt(12345);
         byte[] user = desc.getUserName().getBytes();
         for (int i=0; i<8; i++) out.writeByte(i<user.length ? user[i] : 0);
         out.writeByte(0);
         out.writeByte(0);
         out.writeByte(XrootdProtocol.kXR_asyncap | XrootdProtocol.XRD_CLIENT_CURRENTVER);
         out.writeByte(XrootdProtocol.kXR_useruser);
         out.flush();
         Short handle = Short.valueOf((short)0);
         sendMessage(handle,XrootdProtocol.kXR_login,bos.toByteArray());
         response = new Response(in);
         response.read();
         int dlen = response.getLength();
         DataInputStream rin = response.getInputStream();
         for (int i=0; i<Math.min(dlen,16); i++) rin.read();
         if (dlen>16)
         {
             byte[] security = new byte[dlen-16];
             rin.readFully(security);
             //
             //System.out.println("security="+new String(security));
             // We should really call the security library here to deal with
             // authentification. But no time so
             String fakeResponse = "unix\u0000"+System.getProperty("user.name")+" "+System.getProperty("user.group","nogroup")+"\u0000";
             sendMessage(handle,XrootdProtocol.kXR_auth,null,fakeResponse);
             int status = response.read();
             if (status == XrootdProtocol.kXR_error)
             {
                 in = response.getInputStream();
                 int rc = in.readInt();
                 byte[] errorMessage = new byte[response.getLength()-4];
                 in.readFully(errorMessage);
                 throw new IOException("Xrootd error "+rc+": "+new String(errorMessage,0,errorMessage.length-1));
             }
             else 
             {
                 dlen = response.getLength();
                 rin = response.getInputStream();
                 for (int i=0; i<dlen; i++) rin.read();
             }
         }
         
         // Start a thread which will listen for future responses
         // TODO: It would be better to use a single thread listening on all
         // open sockets
         thread = new Thread(this,"XrootdReader-"+desc.getAddress()+":"+port);
         thread.setDaemon(true);
         thread.start();
         logger.fine(desc+" Success");
      }
      catch (IOException x)
      {
         socket.close();
         throw x;
       }
   }
   public void run()
   {
      try
      {
         for (;!thread.isInterrupted();)
         {            
            response.read();
            int status = response.getStatus();
            Short handle = response.getHandle();
            final ResponseHandler handler;
            synchronized (responseMap)
            {
               handler = (ResponseHandler) responseMap.get(handle);
            }
            
            if (handler == null && status != XrootdProtocol.kXR_attn)
            { 
                if (status == XrootdProtocol.kXR_error)
                {
                    DataInputStream in = response.getInputStream();
                    int rc = in.readInt();
                    byte[] message = new byte[response.getLength()-4];
                    in.readFully(message);
                    logger.log(Level.SEVERE,descriptor+" Out-of-band error "+rc+": "+new String(message,0,message.length-1));
                    continue; // Just carry on in this case??
                }
                throw new IOException(descriptor+" No handler found for handle "+handle+" (status="+status+")");
            }
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
               logger.info(descriptor+" wait: "+new String(message,0,message.length)+" seconds="+seconds);
               
               TimerTask task = new TimerTask()
               {
                  public void run()
                  {
                     try
                     {
                        logger.fine(descriptor+" resending message");
                        handler.sendMessage();
                     }
                     catch (IOException x)
                     {
                        handleSocketException(x);
                     }
                  }
               };
               timer.schedule(task,1000*seconds);
            }
            else if (status == XrootdProtocol.kXR_waitresp)
            {
               DataInputStream in = response.getInputStream();
               int seconds = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               logger.fine(descriptor+" waitresp: "+new String(message,0,message.length)+" seconds="+seconds);                
            }
            else if (status == XrootdProtocol.kXR_redirect)
            {
               DataInputStream in = response.getInputStream();
               int port = in.readInt();
               byte[] message = new byte[response.getLength()-4];
               in.readFully(message);
               String host = new String(message,0,message.length);
               logger.fine(descriptor+" redirect: "+host+" "+port);
               handler.handleRedirect(host,port);
            }
            else if (status == XrootdProtocol.kXR_attn)
            {
               DataInputStream in = response.getInputStream();
               int code = in.readInt();
               if (code == XrootdProtocol.kXR_asynresp)
               {
                   in.readInt(); // reserved
                   // rest should be a standard response, so just loop
                   continue;
               }
               else throw new IOException("Xrootd: Unimplemented asycn message received: "+code);
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
      }
      catch (IOException x)
      {
         handleSocketException(x);
      }
      catch (Throwable x)
      {
          logger.log(Level.SEVERE,descriptor+" multiplexor thread dead!",x);
      }
   }
   private void handleSocketException(IOException x)
   {
      if (!socketClosed)
      {
         // prevent any new requests
         socketClosed = true;
         logger.log(Level.WARNING,descriptor+" Unexpected IO exception on socket",x);
         // Notify anyone listening for a response that we are dead
         List waiting = new ArrayList(responseMap.values());
         for (Iterator i = waiting.iterator(); i.hasNext(); )
         {
            ResponseHandler handler = (ResponseHandler) i.next();
            logger.fine(descriptor+" sending handleSocketError to "+handler);
            handler.handleSocketError(x);
         }
         // Attempt to close socket
         close();
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
      if (socketClosed) throw new IOException("Socket closed");
      this.message.send(handle,message,extra,string);
   }
   
   public String toString() 
   {
      return descriptor.toString();
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
         logger.finer("->"+message);
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
         logger.finer("<-"+handle+" "+status+" "+dataLength);
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
