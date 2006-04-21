package hep.io.root.core;

import hep.io.root.*;


/**
 * Creates RootClass objects
 * @author Tony Johnson (tonyj@slac.stanford.edu)
 * @version $Id$
 */
public interface RootClassFactory
{
   RootClassLoader getLoader();

   BasicRootClass create(String name) throws RootClassNotFound;
}
