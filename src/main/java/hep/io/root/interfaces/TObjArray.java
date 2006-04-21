/*
 * TObjArray.java
 *
 * Created on January 12, 2001, 3:45 PM
 */
package hep.io.root.interfaces;


/**
 *
 * @author tonyj
 * @version $Id$
 */
public interface TObjArray extends hep.io.root.RootObject, TSeqCollection, java.util.List
{
   int getLowerBound();

   int getUpperBound();
}
