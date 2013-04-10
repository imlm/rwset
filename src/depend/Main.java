package depend;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.warnings.Warnings;

import depend.util.Util;

public class Main {
  /**
   * example of use for this class
   * 
   * @param args currently hard-coded (modify appJar)
   * 
   * @throws IOException
   * @throws IllegalArgumentException
   * @throws WalaException
   * @throws CancelException
   * @throws InterruptedException 
   */
  public static void main(String[] args) throws IOException, IllegalArgumentException, WalaException, CancelException, InterruptedException {
    
    // reading and saving command-line properties
    Properties p = CommandLine.parse(args);
    Util.setProperties(p);
    
    // clearing warnings from WALA
    Warnings.clear();
    
    // performing dependency analysis
    MethodDependencyAnalysis an = new MethodDependencyAnalysis(p);    
    if (Util.getBooleanProperty("printWalaWarnings")) {
      System.out.println(Warnings.asString());    
    }
    
    // finding **informed** class and method
    String strClass = Util.getStringProperty("targetClass");
    IClass clazz = an.cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, strClass));
    if (clazz == null) {
      throw new RuntimeException("could not find class!");
    }
    String strMethod = Util.getStringProperty("targetMethod");
    IMethod method = clazz.getMethod(Selector.make(strMethod));
    if (method == null) {
      throw new RuntimeException("could not find class!");
    }
    System.out.printf("data dependencies to method %s\n", method);
    
    // looking for dependencies to method
    Set<IMethod> set = an.getDependencies(method, false, false);
    
    // printing dependencies
    for (IMethod m : set) {
      if (Util.isAppClass(m.getDeclaringClass())) {
        System.out.printf("  %s\n", m);
      }      
    }

  }
}