package hep.io.root.reps;

import hep.io.root.core.AbstractRootObject;
import hep.io.root.core.RootInput;
import hep.io.root.interfaces.TBranch;
import hep.io.root.interfaces.TLeafO;

import java.io.IOException;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.Type;


/**
 * @author Tony Johnson
 * @version $Id$
 */
public abstract class TLeafORep extends AbstractRootObject implements TLeafO, Constants
{
   private Object lastValue;
   private TBranch branch;
   private long lastValueIndex;
   
   public void setBranch(TBranch branch)
   {
      this.branch = branch;
      lastValueIndex = -1;
   }
   
   public Object getValue(long index) throws IOException
   {
      try
      {
         if (index == lastValueIndex)
            return lastValue;
         lastValueIndex = index;
         
         RootInput in = branch.setPosition(this, index);
         String type = in.readString();
         in.readByte();
         return lastValue = in.readObject(type);
         
      }
      catch (IOException x)
      {
         lastValueIndex = -1;
         throw x;
      }
   }
   
   public Object getWrappedValue(long index) throws IOException
   {
      return getValue(index);
   }
   
   public void generateReadCode(InstructionList il, InstructionFactory factory, ConstantPoolGen cp, String className)
   {
      String leafClassName = getClass().getName();
      il.append(factory.createInvoke(leafClassName, "getValue", Type.OBJECT, new Type[]
      {
         Type.LONG
      }, INVOKEVIRTUAL));
   }
   
}
