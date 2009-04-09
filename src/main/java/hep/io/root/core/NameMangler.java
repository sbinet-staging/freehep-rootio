package hep.io.root.core;

/**
 * Controls name mangling when building Java interfaces for Root classes.
 * @author tonyj
 * @version $Id$
 */
public class NameMangler
{
   private final static NameMangler theNameMangler = new NameMangler();

   public static NameMangler instance()
   {
      return theNameMangler;
   }

   /**
    * Name mangling applied to root class names.
    * By default this implementation:
    * <ul>
    * <li>Changes each :: to .
    * <lI>Lowercases anything that precedes the last ::
    * <li>Prepends hep.io.root.interfaces
    * <ul>
    */
   public String mangleInterfaceName(String in)
   {
      return mangleClassName("hep.io.root.interfaces",in);
   }
   String mangleClassName(String prefix, String in)
   {
      return prefix+"."+mangleName(in);
   }
   String mangleName(String in)
   {
      for (;;)
      {
         int pos = in.indexOf("::");
         if (pos < 0)
            break;
         in = in.substring(0, pos).toLowerCase() + "." + in.substring(pos + 2);
      }
      return escapeIllegalCharacters(in);
   }

   /**
    * Name mangling applied to root member variables.
    * By default:
    * <ul>
    * <li>If the name begins with f followed by an uppercase letter, we remove the f
    * <li>If the variable begins with m_ we remove it
    * <li>We uppercase the initial letter
    * <li>Prepend get
    * </ul>
    */
   public String mangleMember(String in)
   {
      if (in.length() >= 2)
      {
         if ((in.charAt(0) == 'f') && Character.isUpperCase(in.charAt(1)))
            in = in.substring(1);
         else if (in.startsWith("m_"))
            in = in.substring(2);
      }
      if (in.length() >= 1)
      {
         if (Character.isLowerCase(in.charAt(0)))
            in = Character.toUpperCase(in.charAt(0)) + in.substring(1);
      }
      return "get" + in;
   }

   private String escapeIllegalCharacters(String in) {
      in = in.replace("<", "$LT$");
      in = in.replace(">", "$GT$");
      return in;
   }
}
