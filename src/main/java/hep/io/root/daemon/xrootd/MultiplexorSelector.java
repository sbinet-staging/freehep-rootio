package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A singleton selector which dispatches calls to mutliplexors as they become ready
 * @author tonyj
 */
class MultiplexorSelector extends Thread {

    private static Logger logger = Logger.getLogger(MultiplexorSelector.class.getName());
    private Selector selector;
    private ConcurrentLinkedQueue<FutureTask> queue = new ConcurrentLinkedQueue<FutureTask>();
    
    MultiplexorSelector()
    {
        super("xrootd-selector");
        setDaemon(true);
        try {
            selector = Selector.open();
        } catch (IOException ex) {
            throw new RuntimeException("Unexpected error creating selector",ex);
        }
    }
    
    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                selector.select();
                for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (key.isValid()) {
                        //System.out.printf("%x %x\n", key.interestOps(), key.readyOps());
                        Multiplexor multiplexor = (Multiplexor) key.attachment();
                        if (key.isConnectable()) {
                            multiplexor.finishConnect();
                        }
                        else if (key.isReadable()) {
                            multiplexor.readResponse();
                        }
                    }
                }
                for (;;) {
                    FutureTask task = queue.poll();
                    if (task == null) break;
                    task.run();
                }
            }
            selector.close();
        } catch (IOException x) {
            logger.log(Level.SEVERE, "Fatal IOException on selector", x);
        }
    }

    /**
     * Register the given multiplexor without blocking.
     * @param multiplexor
     * @return The selection key
     */
    SelectionKey register(final SelectableChannel channel, final Multiplexor multiplexor) throws IOException {
        Callable<SelectionKey> callable = new Callable<SelectionKey>(){
            public SelectionKey call() throws IOException {
                return channel.register(selector,0,multiplexor);
            }
        };
        FutureTask<SelectionKey> future = new FutureTask(callable);
        queue.offer(future);
        selector.wakeup();
        try {
            return future.get();
        } catch (InterruptedException ex) {
            IOException x = new InterruptedIOException("Interrupt while waiting to register selector");
            x.initCause(ex);
            throw x;
        } catch (ExecutionException ex) {
            Throwable x = ex.getCause();
            if (x instanceof IOException) throw (IOException) x;
            else {
                throw new IOException("Error while registering selector",x);
            }
        }
    }
}
