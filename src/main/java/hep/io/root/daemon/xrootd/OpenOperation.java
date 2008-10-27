package hep.io.root.daemon.xrootd;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Open a file.
 * @author tonyj
 */
class OpenOperation extends Operation<OpenFile> {

    OpenOperation(String path, int mode, int options) {
        this(new OpenFile(path, mode, options));
    }

    OpenOperation(OpenFile file) {
        super("open", new OpenMessage(file), new OpenCallback(file));
    }

    private static class OpenMessage extends Message {

        private OpenFile file;

        OpenMessage(OpenFile file) {
            super(XrootdProtocol.kXR_open, file.getPath());
            writeShort(file.getMode());
            writeShort(file.getOptions());
            this.file = file;
        }
    }

    private static class OpenCallback extends Callback<OpenFile> {

        private OpenFile file;

        OpenCallback(OpenFile file) {
            this.file = file;
        }

        public OpenFile responseReady(Response response) throws IOException {
            DataInputStream in = response.getInputStream();
            int handle = in.readInt();
            file.setHandleAndDestination(handle, response.getDestination(), response.getMultiplexor());

            if (response.getLength()>4) file.setCompressionSize(in.readInt());
            if (response.getLength()>8) file.setCompressionType(in.readInt());
            
            if (response.getLength()>12) {
               int dataLength = response.getLength()-12;
               byte[] data = new byte[dataLength];
               in.readFully(data);
               String info = new String(data,"US-ASCII");
               file.setStatus(new FileStatus(info,response.getDestination()));
            }
            return file;
        }
    }
}
