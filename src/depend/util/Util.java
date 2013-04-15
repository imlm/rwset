package depend.util;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.types.TypeReference;

public class Util {

  private static String APP_PREFIX;

  private static Properties PROPS;

  public static void setProperties(Properties _props) {
    PROPS = _props;
    APP_PREFIX = _props.getProperty("appPrefix");  
    if (APP_PREFIX == null) {
      throw new RuntimeException("Please, specifiy \"appPrefix\" parameter");
    }
  }
  
  private static StringBuffer warningMessages = new StringBuffer();
  public static void logWarning(String msg) {
    warningMessages.append(msg);
    warningMessages.append("\n");
  }

  /************** classification of methods and classes ******************/
  public static boolean isRelevantMethod(IMethod meth) {
    IClass klass = meth.getDeclaringClass();
    String pack = klass.getName().getPackage().toString();
    // a => b = !a || b    
    boolean result = 
        (!(pack.startsWith("java/lang") || (klass.getName().toString().startsWith("Ljava/lang/Object")))) &&
        isRelevantClass(klass) && 
        (!meth.isNative() && !meth.isSynthetic());

    return result;
  }

  private static boolean isRelevantClass(IClass klass) {
    //    return classLoader.getName().toString().equals("Application");
    String pack = klass.getName().getPackage().toString();
    return 
        pack.startsWith("java/util") || 
        pack.startsWith(APP_PREFIX);
  }

  public static boolean isAppClass(IClass klass) {
    //  return classLoader.getName().toString().equals("Application");
    String pack = klass.getName().getPackage().toString();
    return pack.startsWith(APP_PREFIX);
  }


  public static IMethod locateIMethods(Set<IMethod> allMethods, String methStr) {
    IMethod result = null;
    int idx = methStr.indexOf(';');
    String klassName = "," + methStr.substring(0, idx).trim() + ">";
    int idx2 = methStr.indexOf('(');
    String methodName = methStr.substring(idx+1, idx2);
    String signature = methStr.substring(idx2);
    for (IMethod tmp : allMethods) {
      String klass = tmp.getDeclaringClass().toString();
      String mName = tmp.getName().toString();
      String descriptorName = tmp.getDescriptor().toString();
      if ((klass.indexOf(klassName) != -1) &&
          mName.equals(methodName) && 
          descriptorName.equals(signature)) {
        result = tmp;
        break;
      }
    }
    if (result == null) {
      throw new RuntimeException("could not find informed method.  Check your input! " +  methStr);
    } 
    return result;
  }

 /**
   * 
   * translates to Randoop format
   */
  public static List<String> toRandoopFormat(Set<IMethod> scopeSet) {
    List<String> result = new ArrayList<String>(scopeSet.size());

    for (IMethod m : scopeSet) {
      StringBuffer sb = new StringBuffer();
      if (m.isInit()) {
        sb.append("cons : ");
      } else {
        sb.append("method : ");
      }

      //classname
      sb.append(m.getDeclaringClass().getReference().getName().toString().substring(1));
      sb.append('.');
      if (m.isInit()) {
        sb.append("<init>");
      } else {
        String name = m.getSelector().toString();
        sb.append(name.substring(0,name.indexOf('(')));
      }

      sb.append('(');
      int nParameters = m.getNumberOfParameters();
      int start;
      if (m.isStatic()) {
        start = 0;
      } else {
        start = 1; //skip 'this'
      }
      boolean hasParameter = false;

      for (int i = start; i < nParameters ; i++) {
        hasParameter = true;
        TypeReference tr = m.getParameterType(i);
        if (tr.isClassType()) {
          sb.append(tr.getName().toString().substring(1));
        } else if (tr.isArrayType()) {
          //          sb.append(tr.getArrayElementType().getName().toString().substring(1));
          TypeReference tarr = tr.getArrayElementType();
          if (tarr.isClassType()) {
            sb.append(tr.getName().toString());
            sb.append(';');
          } else {
            sb.append('[');
            sb.append(tarr.getName().toString());
          }
        } else if (tr.isPrimitiveType()) {
          sb.append(translatePrimitiveCode(tr.getName().toString()));
        } else {
          sb.append(tr.getName().toString());
        }
        sb.append(", ");
      }
      if (hasParameter) {
        sb.deleteCharAt(sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
      }
      sb.append(')');

      String finalname = sb.toString().replace('/', '.');
      result.add(finalname);
      //      System.out.println(finalname);
    }

    return result;
  }

  private static String translatePrimitiveCode(String code) {
    String result;

    if (code.equals("Z")) {
      result = "boolean";
    } else if (code.equals("B")) {
      result = "byte";
    } else if (code.equals("C")) {
      result = "char";
    } else if (code.equals("D")) {
      result = "double";
    } else if (code.equals("F")) {
      result = "float";
    } else if (code.equals("I")) {
      result = "int";
    } else if (code.equals("J")) {
      result = "long";
    } else if (code.equals("S")) {
      result = "short";
    } else if (code.equals("V")) {
      result = "void";
    } else {
      throw new RuntimeException("value unknown!");
    }
    return result;
  }

  public static boolean getBooleanProperty(String string, boolean defaultBoolean) {
    Object o = PROPS.get(string);
    boolean result;
    if (o != null) {
      String s = (String) o;
      result = Boolean.parseBoolean(s);
    } else {
      result = defaultBoolean;
    }
    return result;
  }

  public static boolean getBooleanProperty(String string) {
    return getBooleanProperty(string, false);
  }

  public static String getStringProperty(String propertyName, String defaultString) {
    Object o = PROPS.get(propertyName);
    String result;
    if (o != null) {
      result = (String) o;
    } else {
      result = defaultString;
    }
    return result;
  }

  public static String getStringProperty(String string) {
    return getStringProperty(string, "");
  }
  
  public static Set<IMethod> findAllMethods(ClassHierarchy cha) {
    Set<IMethod> allMethods = new HashSet<IMethod>();
    
    // configuration    
    boolean printAllClasses = getBooleanProperty("listAllClasses");
    if (printAllClasses) {
      System.out.println("All classes:");
    }

    boolean printAppClasses = getBooleanProperty("listAppClasses");
    if (printAppClasses) {
      System.out.println("Application classes:");
    }

    boolean printAppMethods = getBooleanProperty("listAppMethods");

    Set<IClass> visitedClasses = new HashSet<IClass>();
    List<IClass> toVisitClass = new ArrayList<IClass>();
    toVisitClass.add(cha.getRootClass());
    
    while (!toVisitClass.isEmpty()) {
      IClass iclass = toVisitClass.remove(0);

      if (visitedClasses.contains(iclass)) {
        continue;
      }
      visitedClasses.add(iclass);

      toVisitClass.addAll(cha.getImmediateSubclasses(iclass));
      
      if (printAllClasses) {
        System.out.println(iclass);
      }

      if (!Util.isRelevantClass(iclass)) {
        continue;
      }

      boolean toPrint = false;
      if (printAppClasses && Util.isAppClass(iclass)) {
        toPrint = true;
        System.out.println(iclass);
      }

      // iclass was not visited and is a public application class
      for (IMethod imethod : iclass.getAllMethods()) {
        if (imethod.getDeclaringClass() != iclass) {
          continue; // will be visited by superclass
        }

        if (allMethods.contains(imethod)) {
          continue;
        }

        if (toPrint && printAppMethods) {
          System.out.printf(" %s\n", imethod);
        }

        allMethods.add(imethod);
      }
    }

    return allMethods;
    
  }



}