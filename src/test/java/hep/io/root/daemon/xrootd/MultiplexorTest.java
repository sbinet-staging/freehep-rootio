package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.LoginOperation.LoginSession;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 *
 * @author tonyj
 */
public class MultiplexorTest extends TestCase {

    public MultiplexorTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of sendMessage method, of class Multiplexor.
     */
    public void testSendMessage() throws Exception {
        Destination dest = new Destination("wain017.slac.stanford.edu",1094,"tonyj");
        MultiplexorSelector selector = new MultiplexorSelector();
        selector.start();
        Multiplexor m = new Multiplexor(dest,selector);
        
        ResponseListener listener = new TestResponseListener();
        m.connect(listener);        
    }
    private static class TestResponseListener implements ResponseListener {

        private int stage = 0;
        private LoginOperation login = new LoginOperation("anonymous");
        private AuthOperation auth = new AuthOperation();
        
        public void reschedule(long seconds, TimeUnit SECONDS) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void handleError(IOException iOException) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void handleRedirect(String host, int port) throws UnknownHostException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void handleResponse(Response response) throws IOException {
            if (stage == 0)
            {
                int pval = response.readInt();
                int flag = response.readInt();
                stage++; 
                response.getMultiplexor().sendMessage(login.getMessage(),this);
            }
            else if (stage == 1) 
            {
                LoginSession session = login.getCallback().responseReady(response);
                System.out.println(session);
                stage++;
                if (session.getSecurity() != null) {
                    response.getMultiplexor().sendMessage(auth.getMessage(), this);
                }
            }
            else if (stage == 2) 
            {
                auth.getCallback().responseReady(response);
                System.out.println("done");
            }
        }

        public void handleSocketError(IOException iOException) {
            System.out.println("Error Occurred");
        }
        
    }
}
