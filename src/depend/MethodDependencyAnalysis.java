package depend;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.ReverseIterator;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.traverse.Topological;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.warnings.Warnings;
import com.ibm.wala.viz.DotUtil;

import depend.util.CallGraphGenerator;
import depend.util.Timer;
import depend.util.Util;

/**
 * Method dependency analysis based on: 
 * 
 *   - Read-Write (RW) sets to object fields 
 *   - propagation of accesses through private method calls
 * 
 * @author damorim
 *
 */
public class MethodDependencyAnalysis {

  /**
   * constants
   */
  // this is expensive: it demands the creation of the 
  // application call graph
  private static final boolean PROPAGATE_CALLS = true;

  /**
   * instance fields
   */
  AnalysisScope scope;
  ClassHierarchy cha;
  private AnalysisOptions options;
  // several caches
  private static AnalysisCache cache;
  // application(instance)-specific caches
  private Map<IMethod, RWSet> rwSets = new HashMap<IMethod, RWSet>();

  /**
   * parse input and call init
   * 
   * @param args
   * @throws IOException
   * @throws IllegalArgumentException
   * @throws WalaException
   * @throws CancelException
   */
  public MethodDependencyAnalysis(Properties p) throws IOException, IllegalArgumentException, WalaException, CancelException {
    init(p);
  }

  /**
   * clear all internal data
   * 
   * @throws Exception
   */
  public void reset() throws Exception {
    scope = null;
    cha = null;
    options = null;
    cache = null;
    rwSets = new HashMap<IMethod, RWSet>();
  }


  /**
   * all the work is done here! 
   * 
   * in several steps
   * 
   * @param appJar path to the .jar file with all .class application class files
   * @param exclusionFile a WALA specification indicating what class files to ignore
   * 
   * @throws IOException
   * @throws ClassHierarchyException
   * @throws WalaException
   * @throws CancelException
   */
  private void init(Properties p) throws IOException, ClassHierarchyException, WalaException, CancelException {

    String appJar = (String) p.get("appJar");
    if (appJar == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
    String exclusionFile = p.getProperty("exclusionFile", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    Warnings.clear();
    File tmp = (exclusionFile != null) ? (new FileProvider()).getFile(exclusionFile) : new File(CallGraphTestUtil.REGRESSION_EXCLUSIONS);

    Timer timer = new Timer();
    boolean debugTime = Boolean.parseBoolean(p.getProperty("measureTime"));


    if (debugTime) { timer.start(); }
    // very important: define scope of analysis!
    //  + relevant: what is in appJar and Java libraries
    //  - not relevant: what is in exclusionFile
    scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, tmp);    
    if (debugTime) { timer.stop(); System.out.println("<< timing"); timer.show("done building scope"); }
    
    // Class Hierarchy Analysis (CHA)
    cha = ClassHierarchy.make(scope);
    options = new AnalysisOptions(scope, null);
    cache = new AnalysisCache();
    if (debugTime) { timer.stop(); timer.show("done building CHA"); }

    // Extracting direct read-write sets for all methods  
    // and classes within the analysis scope
    directRWSets();    
    if (debugTime) { timer.stop(); timer.show("building direct RW Sets"); }


    if (PROPAGATE_CALLS) {

      // building the call graph
      CallGraphGenerator cgg = new CallGraphGenerator(scope, cha);
      Graph<CGNode> graph = cgg.getCallGraph();
      if (debugTime) { timer.stop(); timer.show("done building call graph"); }

      if (Util.getBooleanProperty("genCallGraph")) {
        System.out.println("=== (dot)");
        DotUtil.dotify(graph, null, PDFTypeHierarchy.DOT_FILE, "/tmp/cg.pdf", Util.getStringProperty("dotPath"));
        System.out.println("===");
      }

      // propagate RWSet from callees (only private methods) to callers
      propagateRWSets(graph);
      if (debugTime) { timer.stop(); timer.show("done propagating RW sets"); }

    }

    if (debugTime) { System.out.println(">> timing"); }

  }

  /**
   * This method navigates through each and every method in
   * the scope of analysis (see scope) and builds a RWSet 
   * object for that method. It uses CHA to perform the 
   * navigation.
   */
  private void directRWSets() {
    // finds rw-sets for all application methods
    List<IClass> toVisit = new ArrayList<IClass>();
    IClass root = cha.getRootClass();
    toVisit.add(root);
    Set<IMethod> visited = new HashSet<IMethod>();
    while (!toVisit.isEmpty()) {      
      IClass cur = toVisit.remove(0);
      toVisit.addAll(cha.getImmediateSubclasses(cur));
      for (IMethod imethod : cur.getAllMethods()) {
        if (imethod.isNative() || imethod.isAbstract() || visited.contains(imethod)) { // abstract methods have no body
          continue;
        }
        visited.add(imethod);
        updateRWSet(imethod);
      }
    }
  }

  /***
   * This method builds a RWSet for a given method.
   * It does **not** consider indirect field reads and 
   * writes through other (say, private) methods
   **/
  private void updateRWSet(IMethod imethod) {
    // cache results for this call
    RWSet result = rwSets.get(imethod);
    if (result != null) {
      return;
    }

    IR ir = cache.getIRFactory().makeIR(imethod, Everywhere.EVERYWHERE, options.getSSAOptions());

    //    boolean here = false;
    //    if (imethod.toString().contains("addRecipe")) {
    //      System.out.println(ir);
    //      here = true;
    //      System.out.println("STOP!");
    //    }

    Set<FieldReference> readSet = new HashSet<FieldReference>();
    Set<FieldReference> writeSet = new HashSet<FieldReference>();
    Map<Integer, FieldReference> ssaVar = new HashMap<Integer, FieldReference>();

    for (SSAInstruction ins : ir.getInstructions()) {
      if (ins == null) continue;
      int kind = -1;
      if (ins instanceof SSAGetInstruction) {
        kind = 0;
      } else if (ins instanceof SSAPutInstruction) {
        kind = 1;
      } else if (ins instanceof SSAArrayLoadInstruction) {
        kind = 2;
      } else if (ins instanceof SSAArrayStoreInstruction) {
        kind = 3;
      }
      switch (kind) {
      case -1: break;
      case 0:
      case 1:
        SSAFieldAccessInstruction fai = (SSAFieldAccessInstruction) ins;
        FieldReference fr = fai.getDeclaredField();
        Set<FieldReference> set = (kind == 0) ? readSet : writeSet;
        set.add(fr);
        // remembering ssa-definition if one exists
        int def = fai.getDef();
        if (def != -1) {
          ssaVar.put(def, fr);
        }
        break;
      case 2:        
      case 3:
        SSAArrayReferenceInstruction ari = (SSAArrayReferenceInstruction) ins;
        int arRef = ari.getArrayRef();
        fr = ssaVar.get(arRef);
        if (fr != null) {
          set = (kind == 2) ? readSet : writeSet;
          set.add(fr);
        }
        break;
      default:
        throw new RuntimeException("UNEXPECTED");
      }
    }

    result = new RWSet(readSet, writeSet);
    rwSets.put(imethod, result);
  }

  /***
   * This method propagates information of RWSet from callees 
   * to callers.  It needs call-graph information to identify 
   * the callees of a method.
   * 
   * This propagation is important since otherwise the impact 
   * of the call of the private method may not be observable.
   * 
   * @param appJar
   * @param exclusionFile
   * @throws IllegalArgumentException
   * @throws WalaException
   * @throws CancelException
   * @throws IOException
   **/
  private void propagateRWSets(Graph<CGNode> graph) throws IllegalArgumentException, WalaException, CancelException, IOException {
    Iterator<CGNode> it = ReverseIterator.reverse(Topological.makeTopologicalIter(graph));
    while (it.hasNext()) {
      CGNode node = it.next();
      IMethod cMethod = node.getMethod();
      if (!Util.isRelevantMethod(cMethod)) {
        continue;
      }
      RWSet rwSetC = null;
      Iterator<CGNode> it2 = graph.getPredNodes(node);
      while (it2.hasNext()) {
        CGNode p = it2.next();
        IMethod pMethod = p.getMethod();
        if (!Util.isRelevantMethod(pMethod)) {
          continue;
        }
        RWSet rwSetP = rwSets.get(pMethod);
        if (rwSetP == null) {
          System.err.printf("no RW-set info for method %s\n", pMethod);
          continue;
        }
        if (rwSetC == null) {
          rwSetC = rwSets.get(cMethod);
          if (rwSetC == null) {
            //TODO: we need to solve this!
            System.err.printf("no RW-set info for method %s\n", cMethod);
            continue;
          }
        }
        // propagate!
        rwSetP.writeSet.addAll(rwSetC.writeSet);  
        rwSetP.readSet.addAll(rwSetC.readSet);
      }
    }
  }

  public Set<IMethod> localizeMethodWriters(Set<FieldReference> reads, boolean onlyPublicClass, boolean onlyPublicMethod) {
    Set<IMethod> result = new HashSet<IMethod>();
    for (FieldReference fread : reads) {
      for (Map.Entry<IMethod, RWSet> e1 : rwSets.entrySet()) {
        IMethod writer = e1.getKey();
        if (onlyPublicClass && !writer.getDeclaringClass().isPublic()) {
          continue;
        }
        if (onlyPublicMethod && !writer.isPublic()) {
          continue;
        }
        Set<FieldReference> writeSet = e1.getValue().writeSet;
        if (writeSet.contains(fread)) {
          result.add(writer);
        }
      }
    }
    return result;
  }

  public Set<FieldReference> getFieldReads(IMethod imeth) {
    return rwSets.get(imeth).readSet;
  }


  public Set<IMethod> getDependencies(IMethod method, boolean onlyPublicClasses, boolean onlyPublicMethods) {
    if (method == null) {
      throw new RuntimeException("Could not find informed method!");
    } 
    Set<FieldReference> reads = getFieldReads(method);
    return localizeMethodWriters(reads , onlyPublicClasses, onlyPublicMethods);
  }


}