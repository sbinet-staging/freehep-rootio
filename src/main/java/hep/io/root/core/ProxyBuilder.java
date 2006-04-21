package hep.io.root.core;

import hep.io.root.*;

import org.apache.bcel.*;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

import java.util.*;


/**
 *
 * @author tonyj
 * @version $Id$
 */
class ProxyBuilder implements ClassBuilder, Constants
{
   private static boolean debugRoot = System.getProperty("debugRoot") != null;
   private static NameMangler nameMangler = NameMangler.instance();
   private boolean hasHeader;
   
   public ProxyBuilder()
   {
      this(true);
   }
   
   protected ProxyBuilder(boolean hasHeader)
   {
      this.hasHeader = hasHeader;
   }
   
   public String getStem()
   {
      return "hep.io.root.proxy";
   }
   
   public JavaClass build(GenericRootClass klass)
   {
      String className = getStem() + "." + klass.getClassName();
      ClassGen cg = new ClassGen(className, "hep/io/root/core/AbstractRootObject", "<generated>", ACC_PUBLIC | ACC_SUPER, new String[]
      {
         nameMangler.mangleClass(klass.getClassName())
      });
      ConstantPoolGen cp = cg.getConstantPool();
      InstructionList il = new InstructionList();
      InstructionFactory factory = new InstructionFactory(cg);
      
      cg.addEmptyConstructor(ACC_PUBLIC);
      
      // Build the complete list of superclasses
      List sup = new ArrayList();
      RootClass[] superClasses = klass.getSuperClasses();
      iterativelyAdd(sup, superClasses);
      sup.add(klass);
      
      // Look for any representations
      for (ListIterator i = sup.listIterator(sup.size()); i.hasPrevious();)
      {
         BasicRootClass rc = (BasicRootClass) i.previous();
         String repName = "hep.io.root.reps." + rc.getClassName() + "Rep";
         try
         {
            Class repClass = Class.forName(repName);
            JavaClass rep = Repository.lookupClass(repClass);
            if (rep != null)
            {
               Field[] repFields = rep.getFields();
               ConstantPoolGen oldCp = new ConstantPoolGen(rep.getConstantPool().copy());
               for (int j = 0; j < repFields.length; j++)
               {
                  if (repFields[j].isStatic())
                     continue;
                  else if (cg.containsField(repFields[j].getName()) == null)
                     cg.addField(new FieldGen(repFields[j], cp).getField());
               }
               
               Method[] repMethods = rep.getMethods();
               for (int j = 0; j < repMethods.length; j++)
               {
                  if (repMethods[j].isAbstract())
                     continue;
                  if (repMethods[j].getName().equals("<init>"))
                     continue;
                  if (cg.containsMethod(repMethods[j].getName(), repMethods[j].getSignature()) != null)
                     continue;
                  
                  MethodGen mg = new MethodGen(repMethods[j], className, cp);
                  
                  // We need to convert any constant pool references from the old CP to the new
                  CPFixup fixup = new CPFixup(oldCp, cp, repName, className);
                  for (Iterator it = mg.getInstructionList().iterator();
                  it.hasNext();)
                     ((InstructionHandle) it.next()).accept(fixup);
                  
                  cg.addMethod(mg.getMethod());
               }
            }
         }
         catch (ClassNotFoundException x)
         {}
      }
      
      // Generate the fields
      for (Iterator i = sup.iterator(); i.hasNext();)
         generateFields((RootClass) i.next(), cp, cg);


      Class implementedInterface = null;
      try
      {
         implementedInterface = Class.forName(nameMangler.mangleClass(klass.getClassName()));
      }
      catch (ClassNotFoundException x) 
      {
         //if (debugRoot) throw new RuntimeException("Unable to load interface "+klass.getClassName(),x);
         if (debugRoot) System.out.println("Warning, could not load interface "+klass.getClassName());
      }

      // Generate the accessor methods
      for (Iterator i = sup.iterator(); i.hasNext();)

         generateMethods((RootClass) i.next(), cp, il, factory, cg, className,implementedInterface);

      // Generate the streamer method
      if (cg.containsMethod("readMembers", "(Lhep/io/root/core/RootInput;)V") == null)
      {
         MethodGen mg = new MethodGen(ACC_PUBLIC, Type.VOID, new Type[]
         {
            new ObjectType("hep/io/root/core/RootInput")
         }, new String[] { "in" }, "readMembers", null, il, cp);
         mg.addException("java/io/IOException");
         
         klass.generateStreamer(cp, il, factory, className, hasHeader);
         
         il.append(InstructionConstants.RETURN);
         mg.setMaxStack();
         mg.setMaxLocals();
         cg.addMethod(mg.getMethod());
         il.dispose();
      }
      return cg.getJavaClass();
   }
   
   private void generateFields(RootClass k, ConstantPoolGen cp, ClassGen cg)
   {
      RootMember[] members = k.getMembers();
      for (int i = 0; i < members.length; i++)
      {
         if (cg.containsField(members[i].getName()) == null)
         {
            Type type = ((BasicMember) members[i]).getJavaType();
            if (type != null)
            {
               FieldGen fg = new FieldGen(ACC_PRIVATE, type, members[i].getName(), cp);
               cg.addField(fg.getField());
            }
         }
      }
   }

   private static void generateMethods(RootClass k, ConstantPoolGen cp, InstructionList il, InstructionFactory factory, ClassGen cg, String className, Class implementedInterface)
   {
      RootMember[] members = k.getMembers();
      for (int i = 0; i < members.length; i++)
      {
         Type type = ((BasicMember) members[i]).getJavaType();
         Type returnType = type;
         try
         {
            if (implementedInterface != null) returnType = Type.getType(implementedInterface.getMethod(nameMangler.mangleMember(members[i].getName()),(Class[]) null).getReturnType());
            if (!returnType.equals(type) && debugRoot)
            {
               System.err.println("Warning: Interface type mismatch "+implementedInterface.getName()+"."+nameMangler.mangleMember(members[i].getName())+" "+returnType+" "+type);
            }
         }
         catch (NoSuchMethodException x)
         {
         }
         if (cg.containsMethod(nameMangler.mangleMember(members[i].getName()), "()" + returnType.getSignature()) == null)
         {
            MethodGen mg = new MethodGen(ACC_PUBLIC, returnType, null, null, nameMangler.mangleMember(members[i].getName()), null, il, cp);
            if (members[i].getType() == null) // Dummy object
            {
               il.append(new PUSH(cp, "<<Unreadable>>"));
            }
            else
            {
               il.append(InstructionConstants.ALOAD_0);
               il.append(factory.createGetField(className, members[i].getName(), type));
            }
            if (!returnType.equals(type)) il.append(factory.createCast(type,returnType));
            il.append(InstructionFactory.createReturn(returnType));
            mg.setMaxStack();
            mg.setMaxLocals();
            cg.addMethod(mg.getMethod());
            il.dispose();
         }
      }
   }
   
   private void iterativelyAdd(List list, RootClass[] superClasses)
   {
      for (int i = 0; i < superClasses.length; i++)
      {
         RootClass[] supsup = superClasses[i].getSuperClasses();
         iterativelyAdd(list, supsup);
         list.add(superClasses[i]);
      }
   }
   
   private static class CPFixup extends org.apache.bcel.generic.EmptyVisitor
   {
      private ConstantPoolGen newCP;
      private ConstantPoolGen oldCP;
      private String newClass;
      private String oldClass;
      
      CPFixup(ConstantPoolGen oldCP, ConstantPoolGen newCP, String oldClass, String newClass)
      {
         this.oldCP = oldCP;
         this.newCP = newCP;
         this.oldClass = oldClass;
         this.newClass = newClass;
      }
      
      public void visitCPInstruction(CPInstruction cpi)
      {
         int index = cpi.getIndex();
         Constant oldConstant = oldCP.getConstant(index);
         if (oldConstant instanceof org.apache.bcel.classfile.ConstantCP)
         {
            org.apache.bcel.classfile.ConstantCP fr = (org.apache.bcel.classfile.ConstantCP) oldConstant;
            if (fr.getClass(oldCP.getConstantPool()).equals(oldClass))
               fr.setClassIndex(oldCP.addClass(newClass));
         }
         index = newCP.addConstant(oldConstant, oldCP);
         cpi.setIndex(index);
      }
   }
}
