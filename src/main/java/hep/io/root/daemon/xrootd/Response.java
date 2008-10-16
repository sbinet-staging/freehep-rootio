package hep.io.root.daemon.xrootd;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 *
 * @author tonyj
 */
class Response {

    private Destination dest;
    private DataInputStream in;
    private Short handle;
    private int status;
    private int dataLength;
    private boolean dataRead;
    private byte[] data;
    private static Logger logger = Logger.getLogger(Response.class.getName());

    Response(Destination dest, DataInputStream in) {
        this.in = in;
        this.dest = dest;
    }

    int getDataAsInt() throws IOException {
        readData();
        return (data[0] << 24) + (data[1] << 16) + (data[2] << 8) + data[3];
    }

    String getDataAsString() throws IOException {
        readData();
        return new String(data,"US-ASCII");
    }
    
    void readData() throws IOException {
        if (!dataRead)
        {
            data = new byte[dataLength];
            in.readFully(data);
            dataRead = true;
        }
    }

    Destination getDestination() {
        return dest;
    }

    boolean isComplete() {
        return status != XrootdProtocol.kXR_oksofar;
    }

    int read() throws IOException {
        handle = new Short(in.readShort());
        status = in.readUnsignedShort();
        dataLength = in.readInt();
        data = null;
        dataRead = dataLength==0;
        logger.finer("<-" + handle + " " + status + " " + dataLength);
        return 8 + dataLength;
    }

    int getStatus() {
        return status;
    }

    int getLength() {
        return dataLength;
    }

    Short getHandle() {
        return handle;
    }

    DataInputStream getInputStream() {
        return in;
    }
}
