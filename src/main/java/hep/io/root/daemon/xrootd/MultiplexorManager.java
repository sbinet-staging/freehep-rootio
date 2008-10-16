package hep.io.root.daemon.xrootd;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

/**
 * Manages the creation and destruction of multiplexors.
 * @author tonyj
 */
class MultiplexorManager implements Runnable {

    private static Logger logger = Logger.getLogger(MultiplexorManager.class.getName());
    private static MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    private Map<Destination,Multiplexor> multiplexorMap = new HashMap<Destination,Multiplexor>();

    public void run() {
        closeIdleConnections();
    }
    
    synchronized void closeIdleConnections() {
        for (Iterator<Map.Entry<Destination, Multiplexor>> i = multiplexorMap.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<Destination, Multiplexor> entry = i.next();
            Multiplexor m = entry.getValue();
            if (m.isIdle())
            {
                i.remove();
                unregisterMultiplexor(m);
                // FIXME: This should be done asynchronously by queueing a task
                m.close();
                logger.log(Level.FINE,"Closed idle connection: "+m);
            }
        }
    }

    synchronized Multiplexor connect(Destination destination) {
        Multiplexor result = multiplexorMap.get(destination);
        if (result != null && result.isSocketClosed())
        {
            multiplexorMap.remove(result);
            unregisterMultiplexor(result);
            result = null;
        }
        if (result == null)
        {
            try
            {
                result = new Multiplexor(destination);
                multiplexorMap.put(destination,result);
                registerMultiplexor(result);
            }
            catch (IOException x) 
            {
                // FIXME: Do something about this
                logger.log(Level.SEVERE,"Error",x);
            }
        }
        return result;
    }
    private ObjectName getObjectNameForMultiplexor(Multiplexor m) throws MalformedObjectNameException
    {
        return new ObjectName("hep.io.root.daemon.xrootd:type=Multiplexor,name=" + m.toString().replace(":",";"));        
    }

    private void registerMultiplexor(Multiplexor result) {
        try {
            mbs.registerMBean(new StandardMBean(result, MultiplexorMBean.class), getObjectNameForMultiplexor(result));
        } catch (Exception x) {
            logger.log(Level.WARNING, "Could not register multiplexor mbean", x);
        }
    }

    private void unregisterMultiplexor(Multiplexor m) {
        try {
            mbs.unregisterMBean(getObjectNameForMultiplexor(m));
        } catch (Exception x) {
            logger.log(Level.WARNING, "Could not unregister multiplexor mbean", x);
        }
    }
}
