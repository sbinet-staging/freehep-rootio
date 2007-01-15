package hep.io.root.daemon.xrootd;

import java.io.IOException;

/**
 * An abstract class to be extended by all response handlers
 * @author tonyj
 */
abstract class ResponseHandler
{
   private Session handle;
   ResponseHandler(Session handle)
   {
      this.handle = handle;
   }

   void handleRedirect(String host, int port) throws IOException
   {
      handle.redirectConnection(this,host,port);
   }

   void handleError(IOException x)
   {
      handle.responseComplete(x);
   }
   abstract void sendMessage() throws IOException;
   abstract void handleResponse(Multiplexor.Response response) throws IOException;

   void handleSocketError(IOException x)
   {
      handleError(x);
   }
}
