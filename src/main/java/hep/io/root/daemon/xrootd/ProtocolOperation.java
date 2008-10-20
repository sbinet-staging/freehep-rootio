package hep.io.root.daemon.xrootd;

import java.io.DataInputStream;
import java.io.IOException;


/**
 * Obtain the protocol version number and type of server.
 * @author tonyj
 */
class ProtocolOperation extends Operation<String> {
    ProtocolOperation() {
        super("protocol",new Message(XrootdProtocol.kXR_protocol),new ProtocolCallback());
    }
    private static class ProtocolCallback extends Callback<String> {

        @Override
        String responseReady(Response response) throws IOException
        {
            DataInputStream data = new DataInputStream(response.getInputStream());
            int pval = data.readInt();
            int flags = data.readInt();
            return pval+" "+flags;
        }
        
    }
}
