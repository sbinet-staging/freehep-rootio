package hep.io.root.daemon.xrootd;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A multiplexor is responsible for managing a single socket connection to an
 * xrootd server. Many clients may use a single multiplexor at the same time.
 * Each multiplexor has a thread which reads the socket to listen for messages,
 * including asnchronous messages sent be the server. 
 * @author tonyj
 */
class Multiplexor implements MultiplexorMBean {

    private static final int MAX_IDLE = Integer.getInteger("hep.io.root.daemon.xrootd.ConnectionTimeout",60000);
    private static Logger logger = Logger.getLogger(Multiplexor.class.getName());
    private static AtomicInteger pseudoPid = new AtomicInteger(1);
    private Destination descriptor;
    private DataInputStream in;
    private SocketChannel channel;
    private Response response;
    private Thread thread;
    private BitSet handles = new BitSet();
    private Map<Short, ResponseListener> responseMap = new HashMap<Short, ResponseListener>();
    private boolean socketClosed = false;
    private long bytesSent;
    private long bytesReceived;
    private Date createDate = new Date();
    private Date lastActive = new Date();

    Multiplexor(Destination desc) throws IOException {
        logger.fine(desc + " Opening connection");
        this.descriptor = desc;
        int port = desc.getPort();
        channel = SocketChannel.open(desc.getSocketAddress());
        try {
            connect(desc);
            // Start a thread which will listen for future responses
            // TODO: It would be better to use a single thread listening on all open sockets
            thread = new Thread(new SocketReader(), "XrootdReader-" + desc.getAddress() + ":" + port);
            thread.setDaemon(true);
            thread.start();
            logger.fine(desc + " Success");
        } catch (IOException x) {
            channel.close();
            throw x;
        }
    }
    boolean isSocketClosed()
    {
        return socketClosed;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public String getUserName() {
        return descriptor.getUserName();
    }
    
    public String getHostAndPort() {
        return descriptor.getAddressAndPort();
    }

    public Date getLastActive() {
        return lastActive;
    }
    
    public int getOutstandingResponseCount()
    {
        return handles.cardinality();
    }
    
    public long getIdleTime()
    {
        return System.currentTimeMillis()-lastActive.getTime();
    }
    
    public boolean isIdle()
    {
        return getOutstandingResponseCount()==0 && getIdleTime()>MAX_IDLE;
    }
    
    Destination getDestination()
    {
        return descriptor;
    }

    synchronized void sendMessage(Message message, ResponseListener listener) throws IOException {
        short id = allocateHandle();
        try {
            sendMessage(id, message);
            addListener(id, listener);
        } catch (IOException x) {
            freeHandle(id);
            throw x;
        }
    }

    synchronized void close() {
        socketClosed = true;
        // Notify anyone listening for a response that we are dead
        for (ResponseListener listener : responseMap.values())
        {
            logger.fine(descriptor + " sending handleSocketError to " + listener);
            listener.handleSocketError();
        }
        responseMap.clear();
        try
        {
            if (channel.isConnected()) channel.close();
        }
        catch (IOException x)
        {
            logger.log(Level.WARNING,"Error during socket close",x);
        }
    }

    public String toString() {
        return descriptor.toString();
    }

    private synchronized void addListener(short id, ResponseListener listener) {
        responseMap.put(id, listener);
    }

    private void connect(Destination desc) throws IOException, IOException {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.putInt(12,4);
        buffer.putInt(16,2012);
        channel.write(buffer);

        in = new DataInputStream(channel.socket().getInputStream());
        int check = in.readInt();
        if (check == 8) {
            throw new IOException("rootd protocol not supported");
        }
        if (check != 0) {
            throw new IOException("Unexpected initial handshake response");
        }
        int rlen = in.readInt();
        if (rlen != 8) {
            throw new IOException("Unexpected initial handshake length");
        }
        int protocol = in.readInt();
        int mode = in.readInt();
        login(desc, protocol, mode);
    }

    private void login(Destination desc, int protocol, int mode) throws IOException, IOException {

        logger.fine(desc + " Logging in protocol=" + protocol + " mode=" + mode);

        Message message = new Message(XrootdProtocol.kXR_login);
        message.writeInt(pseudoPid.getAndIncrement());
        byte[] user = desc.getUserName().getBytes();
        for (int i = 0; i < 8; i++) {
            message.writeByte(i < user.length ? user[i] : 0);
        }
        message.writeByte(0);
        message.writeByte(0);
        message.writeByte(XrootdProtocol.kXR_asyncap | XrootdProtocol.XRD_CLIENT_CURRENTVER);
        message.writeByte(XrootdProtocol.kXR_useruser);
        message.send((short) 0, channel);

        response = new Response(this,in);
        response.read();

        int dlen = response.getLength();
        DataInputStream rin = response.getInputStream();
        for (int i = 0; i < Math.min(dlen, 16); i++) {
            rin.read();
        }
        if (dlen > 16) {
            byte[] security = new byte[dlen - 16];
            rin.readFully(security);
            //
            //System.out.println("security="+new String(security));
            // We should really call the security library here to deal with
            // authentification. But no time so
            String fakeResponse = "unix\u0000" + System.getProperty("user.name") + " " + System.getProperty("user.group", "nogroup") + "\u0000";
            message = new Message(XrootdProtocol.kXR_auth, fakeResponse);
            message.send((short) 0, channel);
            response.read();
            int status = response.getStatus();
            if (status == XrootdProtocol.kXR_error) {
                in = response.getInputStream();
                int rc = in.readInt();
                byte[] errorMessage = new byte[response.getLength() - 4];
                in.readFully(errorMessage);
                throw new IOException("Xrootd error " + rc + ": " + new String(errorMessage, 0, errorMessage.length - 1));
            } else {
                dlen = response.getLength();
                rin = response.getInputStream();
                for (int i = 0; i < dlen; i++) {
                    rin.read();
                }
            }
        }
    }

    private synchronized void removeListener(short id) {
        responseMap.remove(id);
        handles.clear(id);
    }

    private synchronized short allocateHandle() {

        int handle = handles.nextClearBit(0);
        handles.set(handle);
        return (short) handle;
    }

    private synchronized void freeHandle(int id) {
        handles.clear(id);
    }

    private synchronized void sendMessage(short id, Message message) throws IOException {
        bytesSent += message.send(id,channel);
        lastActive.setTime(System.currentTimeMillis());
    }

    private class SocketReader implements Runnable {

        public void run() {
            try {
                for (; !thread.isInterrupted();) {
                    bytesReceived += response.read();
                    int status = response.getStatus();
                    final Short handle = response.getHandle();
                    final ResponseListener handler;
                    lastActive.setTime(System.currentTimeMillis());
                    synchronized (Multiplexor.this) {
                        handler = responseMap.get(handle);
                    }

                    if (handler == null && status != XrootdProtocol.kXR_attn) {
                        if (status == XrootdProtocol.kXR_error) {
                            DataInputStream in = response.getInputStream();
                            int rc = in.readInt();
                            byte[] message = new byte[response.getLength() - 4];
                            in.readFully(message);
                            logger.log(Level.SEVERE, descriptor + " Out-of-band error " + rc + ": " + new String(message, 0, message.length - 1));
                            continue; // Just carry on in this case??
                        }
                        throw new IOException(descriptor + " No handler found for handle " + handle + " (status=" + status + ")");
                    }
                    if (status == XrootdProtocol.kXR_error) {
                        DataInputStream in = response.getInputStream();
                        int rc = in.readInt();
                        byte[] message = new byte[response.getLength() - 4];
                        in.readFully(message);
                        handler.handleError(new IOException("Xrootd error " + rc + ": " + new String(message, 0, message.length - 1)));
                        removeListener(handle);
                    } else if (status == XrootdProtocol.kXR_wait) {
                        DataInputStream in = response.getInputStream();
                        int seconds = in.readInt();
                        byte[] message = new byte[response.getLength() - 4];
                        in.readFully(message);
                        logger.info(descriptor + " wait: " + new String(message, 0, message.length) + " seconds=" + seconds);
                        handler.reschedule(seconds,TimeUnit.SECONDS);
                        removeListener(handle);
                    } else if (status == XrootdProtocol.kXR_waitresp) {
                        DataInputStream in = response.getInputStream();
                        int seconds = in.readInt();
                        byte[] message = new byte[response.getLength() - 4];
                        in.readFully(message);
                        logger.fine(descriptor + " waitresp: " + new String(message, 0, message.length) + " seconds=" + seconds);
                    } else if (status == XrootdProtocol.kXR_redirect) {
                        DataInputStream in = response.getInputStream();
                        int port = in.readInt();
                        byte[] message = new byte[response.getLength() - 4];
                        in.readFully(message);
                        String host = new String(message, 0, message.length);
                        logger.fine(descriptor + " redirect: " + host + " " + port);
                        handler.handleRedirect(host, port);
                        removeListener(handle);
                    } else if (status == XrootdProtocol.kXR_attn) {
                        DataInputStream in = response.getInputStream();
                        int code = in.readInt();
                        if (code == XrootdProtocol.kXR_asynresp) {
                            in.readInt(); // reserved
                            // rest should be a standard response, so just loop
                            continue;
                        } else {
                            throw new IOException("Xrootd: Unimplemented asycn message received: " + code);
                        }
                    } else if (status == XrootdProtocol.kXR_ok || status == XrootdProtocol.kXR_oksofar) {
                        handler.handleResponse(response);
                        if (status != XrootdProtocol.kXR_oksofar) removeListener(handle);
                    } else {
                        throw new IOException("Xrootd: Unimplemented status received: " + status);
                    }
                }
            } catch (IOException x) {
                handleSocketException(x);
            } catch (Throwable x) {
                logger.log(Level.SEVERE, descriptor + " multiplexor thread dead!", x);
            }
        }

        private void handleSocketException(IOException x) {
            if (!socketClosed) {
                logger.log(Level.WARNING, descriptor + " Unexpected IO exception on socket", x);
                close();
            }
        }
    }
}
