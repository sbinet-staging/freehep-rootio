package hep.io.root.classes;

import hep.io.root.core.*;

import org.apache.bcel.generic.*;


/**
 *
 * @author Tony Johnson
 * @version $Id$
 */
public class TDatime extends GenericRootClass
{
   /** Creates a new instance of TSTring */
   public TDatime(String name, StreamerInfo info)
   {
      super(name, info);
   }

   /**
    * The method used to convert the object to its method type.
    */
   protected String getConvertMethod()
   {
      return "getDate";
   }

   /**
    * The type that will be used when this class is stored as a member, or as a return
    * type from a method.
    */
   protected Type getJavaTypeForMethod()
   {
      return new ObjectType("java.util.Date");
   }
}
