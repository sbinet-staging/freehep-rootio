package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.Callback.DefaultCallback;

/**
 * Close a previously opened file, communications path, or path group.
 * @author tonyj
 */
class CloseOperation extends Operation<Void> {

    private OpenFile file;

    /**
     * Create a close operation
     * @param file The file to close
     */
    CloseOperation(OpenFile file) {
        super("close", new CloseMessage(file), new DefaultCallback());
        this.file = file;
    }

    @Override
    Destination getDestination() {
        return file.getDestination();
    }

    private static class CloseMessage extends Message {

        CloseMessage(OpenFile file) {
            super(XrootdProtocol.kXR_close);
            writeInt(file.getHandle());
        }
    }
}
