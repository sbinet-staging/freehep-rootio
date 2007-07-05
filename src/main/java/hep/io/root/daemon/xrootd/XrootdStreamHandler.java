package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.RootStreamHandler;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * A stream handler for files opened with the root protocol.
 * This stream handler will be created automatically if RootURLStreamFactory
 * is used, or this can be created and passed as an argument to the URL 
 * constructor.
 * @author Tony Johnson
 */
public class XrootdStreamHandler extends RootStreamHandler
{
   protected URLConnection openConnection(URL uRL) throws IOException
   {
      try
      {
         return new XrootdURLConnection(uRL);
      }
      catch (Exception x)
      {
         return super.openConnection(uRL);
      }
   }   
}
