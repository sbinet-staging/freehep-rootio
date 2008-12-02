package hep.io.root.daemon.xrootd;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * Encapsulates a response from the xrootd server.
 * Initially this class reads the response header (always 8 bytes).
 * @author tonyj
 */
class Response {

    private Multiplexor multiplexor;
    private SocketChannel in;
    private ByteBuffer buffer = ByteBuffer.allocate(8);
    private ByteBuffer data;
    private Short handle;
    private int status;
    private int dataLength;
    private int dataRead;
    private static Logger logger = Logger.getLogger(Response.class.getName());
    private IOException responseIncomplete = new ResponseIncomplete();

    /**
     * Create a response object for reading from a specific mumtiiplexor
     * @param multiplexor
     * @param in
     */
    Response(Multiplexor multiplexor, SocketChannel in) {
        this.in = in;
        this.multiplexor = multiplexor;
    }

    SocketChannel getSocketChannel() {
        return in;
    }

    /**
     * In progess means that only a partial response has so far been read (as
     * a result of the non-blocking socket IO used by the multiplexor).
     */
    boolean isInProgress() {
        return dataRead<dataLength;
    }

    /**
     * Find how much data is still to be read in this response
     * @return
     */
    int getRemaining() {
        return dataLength - dataRead;
    }

    void incrementDataRead(int l) {
        dataRead += l;
    }

    /**
     * Read an integer from the data associated with this response.
     * @throws java.io.IOException
     */
    int readInt() throws IOException {
        readData();
        return data.getInt();
    }

    /**
     * Get all of the data from this response as a ByeBuffer
     * @return
     * @throws java.io.IOException
     */
    ByteBuffer getData() throws IOException {
        readData();
        return data;
    }
    /**
     * Read the remaining data associated with this response and convert it
     * to a String.
     * @throws java.io.IOException
     */
    String getDataAsString() throws IOException {
        readData();
        return new String(data.array(), data.position(), data.remaining(), "US-ASCII");
    }

    Multiplexor getMultiplexor() {
        return multiplexor;
    }

    void readData() throws IOException {
        if (isInProgress()) {
            if (data == null) data = ByteBuffer.allocate(dataLength);
            readBuffer(data);
            data.flip();
        }
    }

    void readData(ByteBuffer buffer) throws IOException {
        int oldLimit = -1;
        try
        {
            if (buffer.remaining()+dataRead > dataLength)
            {
                oldLimit = buffer.limit();
                buffer.limit(buffer.position()+dataLength-dataRead);
            }
            readBuffer(buffer);
        }
        finally
        {
            if (oldLimit >= 0) buffer.limit(oldLimit);
        }
    }

    Destination getDestination() {
        return multiplexor.getDestination();
    }

    boolean isComplete() {
        return status != XrootdProtocol.kXR_oksofar && !isInProgress();
    }

    int read() throws IOException {
        if (!isInProgress()) buffer.clear();
        readBuffer(buffer);
        buffer.flip();
        handle = buffer.getShort();
        status = buffer.getShort();
        dataLength = buffer.getInt();
        data = null;
        dataRead = 0;
        logger.finest("<-" + handle + " " + status + " " + dataLength);
        return 8 + dataLength;
    }
    void regurgitate() {
        handle = data.getShort();
        status = data.getShort();
        dataLength = data.getInt();
        dataRead = dataLength;
        logger.finest("<-" + handle + " " + status + " " + dataLength);
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

    @Override
    public String toString() {
        return String.format("Response handle: %d status: %d dataLength: %d dataRead: %d",handle,status,dataLength,dataRead);
    }

    private void readBuffer(ByteBuffer buffer) throws EOFException, IOException {
        int l = in.read(buffer);
        if (l < 0) {
            throw new EOFException();
        }
        dataRead += l;
        if (buffer.remaining() > 0) {
            throw responseIncomplete;
        }
    }
    class ResponseIncomplete extends IOException {
    }
}
