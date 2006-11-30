package hep.io.root.daemon.xrootd;

/**
 * 
 * @author tonyj
 */
public interface ResponseHandler
{
   void handleResponse(XrootdProtocol.Response response);
   void handleRedirect(String host, int port);
   void handleWait(int secs);
}
