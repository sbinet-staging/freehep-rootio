package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.DaemonInputStream;
import java.io.IOException;

/**
 *
 * @author tonyj
 */

class XrootdInputStream extends DaemonInputStream
{
   private static int MAXGETSIZE = -1;
   
   private byte[] buffer;
   private int bpos = 0;
   private int blen = 0;
   private int fh;
   private Session handle;
   private XrootdURLConnection connection;
   
   XrootdInputStream(Session handle, int fh, int bufferSize)
   {
      this.fh = fh;
      this.handle = handle;
      buffer = new byte[bufferSize];
   }
   public int read() throws IOException
   {
      if (bpos >= blen)
      {
         if (!fillBuffer()) return -1;
      }
      int i = buffer[bpos++];
      if (i < 0) i += 256;
      return i;
   }
   
   public void close() throws IOException
   {
      if (handle != null) 
      {
         handle.close(fh);
         handle = null;
         if (connection != null) connection.streamClosed();
      }
   }
   
   public int read(byte[] values, int offset, int size) throws IOException
   {
      if (bpos >= blen)
      {
         long position = this.position+bpos;
         int n = size;
         if (MAXGETSIZE > 0 && n > MAXGETSIZE) n = MAXGETSIZE;
         int l = handle.read(fh,values,position,offset,n);
         if (l > 0) this.position += l;
         else l = -1;
         return l;
      }
      else
      {
         int l = Math.min(size,blen-bpos);
         System.arraycopy(buffer, bpos, values, offset, l);
         bpos += l;
         return l;
      }
   }
   
   public long skip(long skip) throws IOException
   {
      setPosition(getPosition()+skip);
      return skip;
   }
   
   public void setPosition(long pos)
   {
      if (pos>position && pos<position+blen)
      {
         bpos = (int) (pos-position);
      }
      else
      {
         blen = 0;
         bpos = 0;
         super.setPosition(pos);
      }
   }
   
   public int available() throws IOException
   {
      return blen - bpos;
   }
   
   private boolean fillBuffer() throws IOException
   {
      position += bpos;
      bpos = 0;
      int n = buffer.length;
      if (MAXGETSIZE > 0 && n > MAXGETSIZE) n = MAXGETSIZE;
      blen = handle.read(fh,buffer,position,0,n);
      return true;
   }
   
   public long getPosition()
   {
      return position + bpos;
   }
   
   protected void finalize() throws Throwable
   {
      close();
      super.finalize();
   }
   void setConnection(XrootdURLConnection connection)
   {
      this.connection = connection;
   }
}