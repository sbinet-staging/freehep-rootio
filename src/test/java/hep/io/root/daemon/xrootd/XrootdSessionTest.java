package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import junit.framework.TestCase;

/**
 *
 * @author tonyj
 */
public class XrootdSessionTest extends TestCase
{
   
   public XrootdSessionTest(String testName)
   {
      super(testName);
   }

   public void testMultiplexor() throws IOException
   {
      Session handle = new Session("glast-xrootd01.slac.stanford.edu",1094,"tonyj");
      handle.ping();
      String dir = "/u/gl/glast/xrootd/testdata/";
      List files = handle.dirList(dir);
      assertEquals(1,files.size());
      assertEquals("pawdemo.root",files.get(0));
      
      String[] stat = handle.stat(dir+files.get(0));
      assertEquals("353216",stat[1]);
      
      int fh = handle.open(dir+files.get(0),0,XrootdProtocol.kXR_open_read);
      byte[] result = new byte[300000];
      int l = handle.read(fh,result,1024);
      assertEquals(300000,l);
      handle.close(fh);
      
      InputStream in = handle.openStream(dir+files.get(0),0,XrootdProtocol.kXR_open_read);
      int p = 0;
      for (;;)
      {
         int ll = in.read(result);
         if (ll < 0) break;
         p += ll;
      }
      in.close();
      assertEquals(353216,p);
      
   }
   
}
