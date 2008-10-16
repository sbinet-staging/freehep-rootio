package hep.io.root.daemon.xrootd;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * @author tonyj
 */
class Destination {

    private String host;
    private int port;
    private String userName;
    private Destination previous;
    private InetAddress address;

    Destination(String host, int port, String userName) throws UnknownHostException {
        this.host = host;
        this.port = port <= 0 ? XrootdProtocol.defaultPort : port;
        this.userName = userName;
        // Fixme: We need to implement shuffle properly
        this.address = InetAddress.getByName(host);
    }

    String getAddressAndPort() {
        return address+":"+port;
    }

    int getPort() {
        return port;
    }

    Destination getPrevious() {
        return previous;
    }

    Destination getRedirected(String host, int port) throws UnknownHostException {
        Destination dest = new Destination(host, port, this.userName);
        dest.previous = this;
        return dest;
    }

    InetAddress getAddress() {
        return address;
    }

    String getUserName() {
        return userName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Destination) {
            Destination that = (Destination) obj;
            return this.address.equals(that.address) &&
                    this.port == that.port &&
                    this.userName.equals(that.userName);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return address.hashCode() + port + userName.hashCode();
    }

    @Override
    public String toString() {
        return "[" + address + ":" + port + ":" + userName + "]";
    }
}
