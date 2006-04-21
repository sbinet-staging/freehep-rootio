package hep.io.root;

/**
 * A representation of a RootObject
 * @author Tony Johnson (tonyj@slac.stanford.edu)
 * @version $Id$
 */
public interface RootObject
{
   /**
    * Get the class of this object
    * @return The RootClass for this object
    */
   RootClass getRootClass();
}
