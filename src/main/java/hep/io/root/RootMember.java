package hep.io.root;


/**
 * Represents a single member of a RootClass
 * @author tonyj
 * @version $Id$
 */
public interface RootMember
{
   public int getArrayDim();

   public String getComment();

   public String getName();

   public RootClass getType();

   public Object getValue(RootObject object);
}
