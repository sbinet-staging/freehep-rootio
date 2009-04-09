package hep.io.root.core;

import org.apache.bcel.classfile.JavaClass;


/**
 * Interface implemented by all class builders
 * @author tonyj
 * @version $Id$
 */
public interface ClassBuilder
{
   String getStem();

   JavaClass build(GenericRootClass name);
}
