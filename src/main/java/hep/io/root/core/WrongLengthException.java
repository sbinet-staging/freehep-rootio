/*
 * WrongLengthException.java
 *
 * Created on January 2, 2002, 11:09 AM
 */
package hep.io.root.core;


/**
 *
 * @author tonyj
 * @version $Id$
 */
public class WrongLengthException extends java.io.IOException
{
   public WrongLengthException(long offset, String className)
   {
      super("Unexpected Length for class " + className + " (offset " + offset + ")");
   }
}
