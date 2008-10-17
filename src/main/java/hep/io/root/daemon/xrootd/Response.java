package hep.io.root.daemon.xrootd;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Logger;

/**
 *
 * @author tonyj
 */
class Response {

    private Multiplexor multiplexor;
    private DataInputStream in;
    private Short handle;
    private int status;
    private int dataLength;
    private boolean dataRead;
    private byte[] data;
    private static Logger logger = Logger.getLogger(Response.class.getName());

    Response(Multiplexor multiplexor, DataInputStream in) {
        this.in = in;
        this.multiplexor = multiplexor;
    }

    int getDataAsInt() throws IOException {
        readData();
        return (data[0] << 24) + (data[1] << 16) + (data[2] << 8) + data[3];
    }

    String getDataAsString() throws IOException {
        readData();
        return new String(data,"US-ASCII");
    }

    Multiplexor getMultiplexor() {
        return multiplexor;
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
        return multiplexor.getDestination();
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
        logger.finest("<-" + handle + " " + status + " " + dataLength);
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
