package hep.io.root.daemon.xrootd;

import java.io.IOException;

/**
 * An abstract class to be extended by all response handlers
 * @author tonyj
 */
abstract class ResponseHandler
{
   private Session session;
   ResponseHandler(Session session)
   {
      this.session = session;
   }
   
   void handleRedirect(String host, int port) throws IOException
   {
      session.redirectConnection(this,host,port);
   }
   
   void handleError(IOException x)
   {
      session.responseComplete(x);
   }
   abstract void sendMessage() throws IOException;
   void handleResponse(Multiplexor.Response response) throws IOException
   {
      session.responseComplete();
   }
   
   void handleSocketError(IOException x)
   {
      // Try reopening initial connection
      // This code is experimental and does not really work. It does not handle the 
      // fact that the session may have open file handles, nor does it handle thread
      // safety issues for sessions which have their multiplexor changed while they are
      // in the middle of sending a request.
      try
      {
         session.redirectToInitial(this);
      }
      catch (IOException xx)
      {
         IOException xxx = new IOException("Error handling socket error "+x.getMessage());
         xxx.initCause(xx);
         handleError(xxx);
      }
   }
}
