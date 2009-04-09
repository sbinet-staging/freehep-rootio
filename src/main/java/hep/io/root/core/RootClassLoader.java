package hep.io.root.core;

import hep.io.root.RootClassNotFound;
import hep.io.root.RootFileReader;
import hep.io.root.test.JasminVisitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.bcel.classfile.JavaClass;

/**
 *
 * @author tonyj
 */
public class RootClassLoader extends ClassLoader
{
   private final static boolean debugRoot = System.getProperty("debugRoot") != null;
   private Map classMap = new HashMap();
   private Map map = new HashMap();
   private RootFileReader rfr;
   private final static Object bcel = new Object();

   RootClassLoader(RootFileReader rfr)
   {
      super(RootClassLoader.class.getClassLoader());
      this.rfr = rfr;
      register(new InterfaceBuilder());
      register(new ProxyBuilder());
      register(new Proxy2Builder());
      register(new ClonesBuilder());
      register(new CloneBuilder());
      register(new Clone2Builder());
   }

   public Class findClass(String name) throws ClassNotFoundException
   {
      try
      {
         if (debugRoot)
            System.out.println("RootClassLoader: loading " + name);

         int pos = name.lastIndexOf('.');
         String pkg = name.substring(0, pos);
         ClassBuilder builder = (ClassBuilder) map.get(pkg);
         if (builder == null)
            throw new ClassNotFoundException(name);

         String rootClassName = name.substring(pos + 1);
         // FIXME: Do something better
         rootClassName = rootClassName.replace("$LT$","<");
         rootClassName = rootClassName.replace("$GT$",">");
         GenericRootClass gc = (GenericRootClass) rfr.getFactory().create(rootClassName);
         JavaClass jc;
         // BCEL is not thread safe, so class building must be synchronized
         synchronized (bcel)
         {
             jc = builder.build(gc);
         }

         if (debugRoot)
         {
            try
            {
               FileOutputStream out = new FileOutputStream(name + ".j");
               new JasminVisitor(jc, out).disassemble();
               out.close();
            }
            catch (IOException x) {}
         }

         byte[] data = jc.getBytes();
         Class result = defineClass(name, data, 0, data.length);
         classMap.put(result, gc);
         return result;
      }
      catch (RootClassNotFound x)
      {
         throw new ClassNotFoundException(name);
      }
   }

   public Class loadSpecial(ClassBuilder builder, String name, GenericRootClass rc)
   {
      if (debugRoot)
         System.out.println("RootClassLoader: loading special " + name);

      JavaClass jc = builder.build(rc);
      if (debugRoot)
      {
         try
         {
            FileOutputStream out = new FileOutputStream(name + ".j");
            new JasminVisitor(jc, out).disassemble();
            out.close();
         }
         catch (IOException x) {}
      }

      byte[] data = jc.getBytes();
      Class result = defineClass(name, data, 0, data.length);
      classMap.put(result, rc);
      return result;
   }

   GenericRootClass getRootClass(Class klass)
   {
      return (GenericRootClass) classMap.get(klass);
   }

   private void register(ClassBuilder builder)
   {
      map.put(builder.getStem(), builder);
   }
}
