package hep.io.root.daemon.xrootd;

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
        Session session = new Session("glast-rdr.slac.stanford.edu",1094,"tonyj");
        session.ping();
    }
}
