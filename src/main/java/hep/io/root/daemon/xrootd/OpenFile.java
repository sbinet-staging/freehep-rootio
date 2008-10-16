package hep.io.root.daemon.xrootd;

/**
 * A class representing an open xrootd file. It encapsulates a handle used 
 * internally, and the destination with which the file is associated.
 * The handle and destination may be changed as a result of an error or a 
 * redirect.
 * @author tonyj
 */
class OpenFile {

    private int handle;
    private Destination destination;
    private String path;
    private int mode;
    private int options;

    OpenFile(String path, int mode, int options) {
        this.path = path;
        this.mode = mode;
        this.options = options;
    }

    int getHandle() {
        return handle;
    }

    Destination getDestination() {
        return destination;
    }
    
    void setHandleAndDestination(int handle, Destination destination)
    {
        this.handle = handle;
        this.destination = destination;
    }

    public String getPath() {
        return path;
    }

    public int getMode() {
        return mode;
    }

    public int getOptions() {
        return options;
    }
}