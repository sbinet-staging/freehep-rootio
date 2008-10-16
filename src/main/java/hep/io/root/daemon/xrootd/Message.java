package hep.io.root.daemon.xrootd;

import java.io.DataOutput;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * A message contains all of the information needed to perform a single
 * xrootd operation. A message normally consists of an xrootd op code, an 
 * optional string (such as a file path), and op code dependent extra information
 * which can be encoded into the header. The extra information can be written 
 * to the header by calling the write* methods of this class.
 * @author tonyj
 */
class Message {

    private int message;
    private byte[] extra;
    private int pos = 0;
    private String string;
    private static Logger logger = Logger.getLogger(Response.class.getName());

    /** Create a message from an Xrootd operation code
     * @param message The op code
     */
    Message(int message) {
        this(message, null);
    }
    /**
     * Create a message from an Xrootd operation code plus a string
     * @param message The op code
     * @param string The string to be sent with the message (such as a file path)
     */    
    Message(int message, String string) {
        this.message = message;
        this.string = string;
        this.extra = new byte[16];
    }

    int send(short handle, DataOutput out) throws IOException {
        logger.finest("->" + message);
        out.writeShort(handle);
        out.writeShort(message);
        sendExtra(out);
        int messageLength = 24;
        if (string == null) {
            out.writeInt(0);
        } else {
            byte[] bytes = string.getBytes();
            out.writeInt(bytes.length);
            out.write(bytes);
            messageLength += bytes.length;
        }
        return messageLength;
    }

    void writeByte(int i) {
        extra[pos++] = (byte) (i & 0xff);
    }

    void writeInt(int i) {
        writeByte(i >>> 24);
        writeByte(i >>> 16);
        writeByte(i >>> 8);
        writeByte(i);
    }

    void writeLong(long i) {
        writeInt((int) (i >>> 32));
        writeInt((int) i);
    }

    void writeShort(int i) {
        writeByte(i >>> 8);
        writeByte(i);        
    }

    /**
     * This method can be overriden by classes that want to send the extra bytes
     * in the header themselves. The method must write exactly 16 bytes to the 
     * data output stream.
     * @param out
     * @throws java.io.IOException
     */
    void sendExtra(DataOutput out) throws IOException {
        for (int i = 0; i < 16; i++) {
            out.writeByte(extra == null || i >= extra.length ? 0 : extra[i]);
        }
    }
        

}
