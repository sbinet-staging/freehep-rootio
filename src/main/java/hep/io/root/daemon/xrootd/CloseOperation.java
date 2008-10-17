package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.Callback.DefaultCallback;
import java.io.DataOutput;
import java.io.IOException;

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

    @Override
    Operation getPrerequisite() {
        return new OpenOperation(file);
    }
    
    @Override
    Multiplexor getMultiplexor() {
        return file.getMultiplexor();
    } 
    
    private static class CloseMessage extends Message {

        private OpenFile file;
        CloseMessage(OpenFile file) {
            super(XrootdProtocol.kXR_close);
            this.file = file;
        }
        @Override
        void sendExtra( DataOutput out) throws IOException {
            // Note, we do things this way because the file handle may have changed
            // since we were created, as a result of a redirect.
            out.writeInt(file.getHandle());
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
        }
    }
}
