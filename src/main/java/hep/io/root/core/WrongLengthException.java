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
