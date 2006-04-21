/*
 * StreamerInfo.java
 *
 * Created on January 8, 2001, 2:06 PM
 */
package hep.io.root.core;

import hep.io.root.RootClass;
import hep.io.root.RootClassNotFound;


/**
 * Interface implemented by both StreamerInfoString and StreamerInfoNew
 * @author  tonyj
 * @version $Id$
 */
public abstract class StreamerInfo implements org.apache.bcel.Constants
{
   protected BasicMember[] members;
   protected RootClass[] superClasses;

   abstract int getBits();

   abstract int getCheckSum();

   abstract void resolve(RootClassFactory factory) throws RootClassNotFound;

   BasicMember[] getMembers()
   {
      return members;
   }
   
   BasicMember getMember(String name)
   {
       for (int i=0; i<members.length; i++)
       {
           if (name.equals(members[i].getName())) return members[i];
       }
       return null;
   }

   RootClass[] getSuperClasses()
   {
      return superClasses;
   }

   abstract int getVersion();
}
