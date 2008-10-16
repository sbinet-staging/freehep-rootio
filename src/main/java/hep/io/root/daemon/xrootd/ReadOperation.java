package hep.io.root.daemon.xrootd;

import java.io.IOException;

/**
 * Read from an open file.
 * @author tonyj
 */
class ReadOperation extends Operation<Integer> {

    private OpenFile file;
    ReadOperation(OpenFile file, byte[] buffer, long fileOffset, int bufOffset, int size) {
        super("read", new ReadMessage(file, fileOffset, size), new ReadCallback(buffer, bufOffset));
        this.file = file;
    }

    @Override
    Operation getPrerequisite() {
        return new OpenOperation(file);
    }

    @Override
    Destination getDestination() {
        return file.getDestination();
    }

    private static class ReadMessage extends Message {

        ReadMessage(OpenFile file, long fileOffset, int size) {
            super(XrootdProtocol.kXR_read);
            writeInt(file.getHandle());
            writeLong(fileOffset);
            writeInt(size);
        }
    }

    private static class ReadCallback extends Callback<Integer> {

        private byte[] buffer;
        private int bufOffset;
        private int l;

        ReadCallback(byte[] buffer, int bufOffset) {
            this.buffer = buffer;
            this.bufOffset = bufOffset;
        }

        public Integer responseReady(Response response) throws IOException {
            int dlen = response.getLength();
            response.getInputStream().readFully(buffer, bufOffset + l, dlen);
            l += dlen;
            return l;
        }

        public void clear() {
            l = 0;
        }
    }
}