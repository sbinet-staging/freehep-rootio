package hep.io.root.daemon.xrootd;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * A URLStreamHandlerFactory for registering the root: protocol
 * <p>
 * Usage:
 * <pre>
 *    URL.setURLStreamHandlerFactory(new XrootdURLStreamFactory());
      URL url = new URL("root://root.cern.ch/demo.root");
 * </pre>
 * @author Tony Johnson
 */
public class XrootdURLStreamFactory implements URLStreamHandlerFactory
{
   public URLStreamHandler createURLStreamHandler(String protocol)
   {
      if (protocol.equals("root")) return new XrootdStreamHandler();
      return null;
   }    
}
