package depend;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.viz.DotUtil;

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
    
    
    // looking for dependencies to method
    Set<IMethod> set = an.getDependencies(method, false, false);
    
    String reportType = Util.getStringProperty("reportType").trim();
    
    dumpResults(method, set, reportType);

  }

  private static void dumpResults(IMethod method, Set<IMethod> set,
      String reportType) throws IOException, WalaException {
    if (reportType.equals("list")) {
      
      System.out.printf("data dependencies to method %s\n", method);

      // printing dependencies
      for (IMethod m : set) {
        if (Util.isAppClass(m.getDeclaringClass())) {
          System.out.printf("  %s\n", m);
        }      
      }

    } else if (reportType.equals("dot")) {
      //TODO: you may want to print before propagating 
      //data dependencies
      
      /**
       * generate dot
       */
      StringBuffer sb = new StringBuffer();
      sb.append("digraph \"DirectedGraph\" {\n");
      sb.append(" graph [concentrate = true];\n");
      sb.append(" center=true;\n");
      sb.append(" fontsize=6;\n");
      sb.append(" node [ color=blue,shape=\"box\"fontsize=6,fontcolor=black,fontname=Arial];\n");
      sb.append(" edge [ color=black,fontsize=6,fontcolor=black,fontname=Arial];\n");
      for (IMethod m : set) {
        if (Util.isAppClass(m.getDeclaringClass())) {
          sb.append(m);
          sb.append(" -> ");
          sb.append(method);
          sb.append("\n");
        }      
      }
      sb.append("}\n");
      
      /**
       * results.dot
       */
      File dotFile = new File("/tmp/results.dot");
      String strPdfFile= "/tmp/results.pdf";
      FileWriter fw = new FileWriter(dotFile);
      fw.append(sb);
      fw.flush();
      fw.close();
      DotUtil.spawnDot(Util.getStringProperty("dotPath"), strPdfFile, dotFile);
      
    }
  }
}