package hep.io.root.reps;

import hep.io.root.RootClassNotFound;
import hep.io.root.core.AbstractRootObject;
import hep.io.root.core.GenericRootClass;
import hep.io.root.core.Hollow;
import hep.io.root.core.HollowBuilder;
import hep.io.root.core.RootClassFactory;
import hep.io.root.core.RootInput;
import hep.io.root.interfaces.TBranch;
import hep.io.root.interfaces.TBranchObject;
import hep.io.root.interfaces.TLeafObject;

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
public abstract class TLeafObjectRep extends AbstractRootObject implements TLeafObject, Constants
{
   private Class hollowClass;
   private Object lastValue;
   private RootInput rin;
   private TBranchObject branch;
   private long lastValueIndex;

   public void setBranch(TBranch branch)
   {
      this.branch = (TBranchObject) branch;
      lastValueIndex = -1;
   }

   public Object getValue(long index) throws IOException
   {
      try
      {
         if (index == lastValueIndex)
            return lastValue;
         lastValueIndex = index;

         boolean hollow = branch.getEntryNumber() == 0;
         if (!hollow)
         {
            RootInput in = branch.setPosition(this, index);
            String type = in.readString();
            in.readByte();
            return lastValue = in.readObject(type);
         }
         else
         {
            if (hollowClass == null)
            {
               HollowBuilder builder = new HollowBuilder(branch);
               String name = "hep.io.root.hollow." + branch.getClassName();
               RootClassFactory factory = rin.getFactory();
               GenericRootClass gc = (GenericRootClass) factory.create(branch.getClassName());
               hollowClass = factory.getLoader().loadSpecial(builder, name, gc);

               // Populate the leafs.
               builder.populateStatics(hollowClass, factory);
            }

            Hollow h = (Hollow) hollowClass.newInstance();
            h.setHollowIndex(index);
            return lastValue = h;
         }
      }
      catch (IOException x)
      {
         lastValueIndex = -1;
         throw x;
      }
      catch (RootClassNotFound x)
      {
         lastValueIndex = -1;
         throw new IOException("RootClassNotFound " + x.getClassName());
      }
      catch (Throwable x)
      {
         lastValueIndex = -1;
         IOException io = new IOException("Error instantiating hollow object");
         io.initCause(x);
         throw io;
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

   public void read(RootInput in) throws IOException
   {
      super.read(in);
      rin = in.getTop();
   }
}
