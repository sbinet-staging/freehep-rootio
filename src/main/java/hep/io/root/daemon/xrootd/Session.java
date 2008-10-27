package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A session allows for all supported xrootd commands to be send. All the 
 * methods of this class are synchronous, i.e. they wait until the data is
 * available before they return.
 * @author tonyj
 */
public class Session {

    private static Logger logger = Logger.getLogger(Session.class.getName());
    private Dispatcher dispatcher = Dispatcher.instance();
    private BitSet openFileBitSet = new BitSet();
    private Map<Integer, OpenFile> openFiles = new HashMap<Integer, OpenFile>();
    private Destination destination;

    public Session(String host, int port, String userName) throws IOException {
        this(new Destination(host, port, userName));
    }
    
    Session(Destination dest) throws IOException {
        this.destination = dest;
    }

    public void close() throws IOException {
        // Note, we make a copy to avoid concurrent modification exception 
        // as we close files
        for (int i : new ArrayList<Integer>(openFiles.keySet())) {
            close(i);
        }
    }
    
    /** 
     * This method allows asynchronous execution of an operation.
     * @param operation The operation to be performed
     * @return A future which will contain the response
     */
    public <V> FutureResponse<V> send(Operation<V> operation)
    {
        Destination actualDestination = operation.getDestination();
        if (actualDestination == null) actualDestination = destination;
        return dispatcher.send(actualDestination, operation);
    }
    
    public List<String> dirList(String path) throws IOException {
        return send(new DirListOperation(path)).getResponse();
    }

    public void ping() throws IOException {
        send(new PingOperation()).getResponse();
    }

    public void remove(final String path) throws IOException {
        send(new RemoveOperation(path)).getResponse();
    }

    public FileStatus stat(final String path) throws IOException {
        return send(new StatOperation(path)).getResponse();
    }

    public String query(final int queryType, final String path) throws IOException {
        return send(new QueryOperation(queryType,path)).getResponse();
    }

    public String prepare(String[] path, int options, int priority) throws IOException {
        return send(new PrepareOperation(path,options,priority)).getResponse();
    }
    
    public String[] locate(String path, boolean noWait, boolean refresh) throws IOException {
        return send(new LocateOperation(path,noWait,refresh)).getResponse();
    }
    
    public String protocol() throws IOException {
        return send(new ProtocolOperation()).getResponse();
    }

    public int open(final String path, final int mode, final int options) throws IOException {
        OpenFile file = send(new OpenOperation(path,mode,options)).getResponse();
        return fileHandleForFile(file);
    }

    public void close(int fileHandle) throws IOException {
        send(new CloseOperation(fileForFileHandle(fileHandle))).getResponse();
        freeFileHandle(fileHandle);
    }

    public int read(int fileHandle, byte[] buffer, long fileOffset) throws IOException {
        return read(fileHandle, buffer, fileOffset, 0, buffer.length);
    }

    public int read(int fileHandle, final byte[] buffer, long fileOffset, final int bufOffset, final int size) throws IOException {
        return send(new ReadOperation(fileForFileHandle(fileHandle),buffer,fileOffset,bufOffset,size)).getResponse();
    }
    
    public void write(int handle, byte[] buffer, int offset, int length, long fileOffset) throws IOException {
        send(new WriteOperation(fileForFileHandle(handle),buffer,offset,length,fileOffset));
    }
 
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    private synchronized OpenFile fileForFileHandle(int fileHandle) {
        OpenFile result = openFiles.get(fileHandle);
        if (result == null) throw new IllegalArgumentException("Invalid file handle");
        return result;
    }

    private synchronized int fileHandleForFile(OpenFile file) {
        int handle = openFileBitSet.nextClearBit(0);
        openFileBitSet.set(handle);
        openFiles.put(handle, file);
        return handle;
    }

    private synchronized void freeFileHandle(int fileHandle) {
        openFileBitSet.clear(fileHandle);
        openFiles.remove(fileHandle);
    }

    @Override
    public String toString()
    {
        return destination.toString();
    }
}