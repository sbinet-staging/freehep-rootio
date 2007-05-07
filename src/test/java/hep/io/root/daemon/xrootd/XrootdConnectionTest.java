package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import junit.framework.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;

/**
 *
 * @author tonyj
 */
public class XrootdConnectionTest extends TestCase
{
   public XrootdConnectionTest(String testName)
   {
      super(testName);
   }
   
   public void testConnection() throws MalformedURLException, IOException
   {
      int expectedLength = 353216;
      URL url = new URL(null,"xroot://glast-xrootd01.slac.stanford.edu/u/gl/glast/xrootd/testdata/pawdemo.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      InputStream in = conn.getInputStream();
      try
      {
         assertEquals(expectedLength,conn.getContentLength());
         int size = 0;
         byte[] buffer = new byte[8096];
         for (;;)
         {
            int l = in.read(buffer);
            if (l<0) break;
            size += l;
         }
         assertEquals(expectedLength,size);
      }
      finally
      {
         in.close();
      }
   }
   public void testRedirect()  throws MalformedURLException, IOException
   {
      int expectedLength = 7251570;
      // Fixme: This currently fails outside SLAC. Maybe we can teach the redirector about the test data
      if (!isAtSLAC()) return;
      URL url = new URL(null,"xroot://glast-rdr.slac.stanford.edu//glast/mc/DC2/ChickenLittle-GR-v7r3p24-2/029/615/ChickenLittle-GR-v7r3p24-2_029615_mc_MC.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      InputStream in = conn.getInputStream();
      assertEquals(expectedLength,conn.getContentLength());
      try
      {
         int size = 0;
         byte[] buffer = new byte[8096];
         for (;;)
         {
            int l = in.read(buffer);
            if (l<0) break;
            size += l;
         }
         assertEquals(expectedLength,size);
      }
      finally
      {
         in.close();
      }
   }
   public void testChecksum()  throws MalformedURLException, IOException
   {
      // Fixme: This currently fails outside SLAC. Maybe we can teach the redirector about the test data
      if (!isAtSLAC()) return;
      URL url = new URL(null,"xroot://glast-rdr.slac.stanford.edu//glast/mc/DC2/ChickenLittle-GR-v7r3p24-2/029/615/ChickenLittle-GR-v7r3p24-2_029615_mc_MC.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME_ANONYMOUS);
      conn.connect();
      long cksum = ((XrootdURLConnection) conn).getCheckSum();
      assertEquals(153993336,cksum);
   }
   public void testError() throws MalformedURLException, IOException
   {
      URL url = new URL(null,"xroot://glast-xrootd01.slac.stanford.edu/NoSuchFile", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   public void testError6() throws MalformedURLException, IOException
   {
      
      URL url = new URL(null,"xroot://glast-rdr.slac.stanford.edu/NonExisTantFile", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   public void testError2() throws MalformedURLException, IOException
   {
      
      URL url = new URL(null,"xroot://glast-xrootd01.slac.stanford.edu/u/gl/glast/xrootd/testdata/PAWdemo.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   public void testError5() throws MalformedURLException, IOException
   {
      
      URL url = new URL(null,"root://sldrh2.slac.stanford.edu/pawdemo.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   public void testError3() throws MalformedURLException, IOException
   {
      // Host does not exist, but doesn't run rootd
      URL url = new URL(null,"xroot://badHost.slac.stanford.edu/u/gl/glast/xrootd/testdata/PAWdemo.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   public void testError4() throws MalformedURLException, IOException
   {
      // Host exists, but doesn't run rootd
      URL url = new URL(null,"xroot://www.slac.stanford.edu/u/gl/glast/xrootd/testdata/PAWdemo.root", new XrootdStreamHandler());
      URLConnection conn = url.openConnection();
      conn.setRequestProperty(XrootdURLConnection.XROOT_AUTHORIZATION_SCHEME,"anonymous");
      try
      {
         InputStream in = conn.getInputStream();
         fail("Should have thrown an exception");
      }
      catch (IOException x)
      {
         // OK, expected
      }
   }
   static boolean isAtSLAC()
   {
      try
      {
         Enumeration nie = NetworkInterface.getNetworkInterfaces();
         while (nie.hasMoreElements())
         {
            NetworkInterface ni = (NetworkInterface) nie.nextElement();
            Enumeration ie = ni.getInetAddresses();
            while (ie.hasMoreElements())
            {
               InetAddress address = (InetAddress) ie.nextElement();
               if (address.getHostAddress().startsWith("134.79")) return true;
            }
         }
         return false;
      }
      catch (SocketException x)
      {
         return false;
      }
   }
}