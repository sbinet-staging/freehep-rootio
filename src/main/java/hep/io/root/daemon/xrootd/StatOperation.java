package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.StatOperation.FileStatus;
import java.io.IOException;
import java.util.Date;

/**
 * Obtain status information for a path.
 * @author tonyj
 */
class StatOperation extends Operation<FileStatus> {

    /**
     * Create the StatOperation.
     * @param path Is the path whose status information is to be returned.
     */
    StatOperation(String path) {
        super("stat", new Message(XrootdProtocol.kXR_stat, path),new StatCallback());
    }

    private static class StatCallback extends Callback<FileStatus> {

        FileStatus responseReady(Response response) throws IOException {
            return new FileStatus(response.getDataAsString(),response.getDestination());
        }
    }
    static class FileStatus
    {
        private String id;
        private long size;
        private int flags;
        private Date modTime;
        private Destination destination;
        
        FileStatus(String response, Destination destination)
        {
            String[] tokens = response.replace("\u0000", "").split(" +");
            id = tokens[0];
            size = Long.parseLong(tokens[1]);
            flags = Integer.parseInt(tokens[2]);
            modTime = new Date(Long.parseLong(tokens[3])*1000);
            this.destination = destination;
        }

        public int getFlags() {
            return flags;
        }

        public String getId() {
            return id;
        }

        public Date getModTime() {
            return modTime;
        }

        public long getSize() {
            return size;
        }

        @Override
        public String toString() {
            return String.format("location=%s id=%s\nsize=%,d lastModified=%s flags=%d",destination,id,size,modTime,flags);
        }
        
        /**
         * The location that returned the file information.
         * @return The location that returned the file information.
         */
        public Destination getFileLocation() {
            return destination;
        }
    }
}
