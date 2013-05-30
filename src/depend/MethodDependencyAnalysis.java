package depend;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cfg.AbstractInterproceduralCFG;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
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
import depend.util.graph.Edge;
import depend.util.graph.SimpleGraph;

/**
 * Method dependency analysis based on:
 * 
 * - Read-Write (RW) sets to object fields - propagation of accesses through
 * private method calls
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
  private ClassHierarchy cha;
  AnalysisOptions options;
  // several caches
  static AnalysisCache cache;
  // application(instance)-specific caches
  private Map<IMethod, RWSet> rwSets = new HashMap<IMethod, RWSet>();

  /**
   * Callgraph generator for this analysis
   */
  private CallGraphGenerator cgGenerator;

  Timer timer = new Timer();
  Properties p;
  /**
   * parse input and call init
   * 
   * @param args
   * @throws IOException
   * @throws IllegalArgumentException
   * @throws WalaException
   * @throws CancelException
   */
  public MethodDependencyAnalysis(Properties p) throws IOException,
      IllegalArgumentException, WalaException, CancelException {
    
    this.p = p;  
    
    setup();
    
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
   * @param appJar
   *          path to the .jar file with all .class application class files
   * @param exclusionFile
   *          a WALA specification indicating what class files to ignore
   * 
   * @throws IOException
   * @throws ClassHierarchyException
   * @throws WalaException
   * @throws CancelException
   */
  public void run() throws IOException, ClassHierarchyException,
      WalaException, CancelException {
    
    boolean debugTime = Boolean.parseBoolean(p.getProperty("measureTime"));

    // Extracting direct read-write sets for all methods
    // and classes within the analysis scope
    directRWSets();
    if (debugTime) {
      timer.stop();
      timer.show("building direct RW Sets");
    }

    if (PROPAGATE_CALLS) {

      // building the call graph
      CallGraphGenerator cgg = this.getCallGraphGenerator();
      Graph<CGNode> graph = cgg.getPrunedCallGraph();
      if (debugTime) {
        timer.stop();
        timer.show("done building call graph");
      }

      if (Util.getBooleanProperty("genCallGraph")) {
        System.out.println("=== (dot)");
        DotUtil.dotify(graph, null, PDFTypeHierarchy.DOT_FILE, "/tmp/cg.pdf",
            Util.getStringProperty("dotPath"));
        System.out.println("===");
      }

      // propagate RWSet from callees (only private methods) to callers
      propagateRWSets(graph);
      if (debugTime) {
        timer.stop();
        timer.show("done propagating RW sets");
      }

    }

    if (debugTime) {
      System.out.println(">> timing");
    }
    
    if (Util.getBooleanProperty("printWalaWarnings")) {
      System.out.println(Warnings.asString());
    }

  }

  private void setup() throws IOException,
      ClassHierarchyException {
    String appJar = (String) p.get("appJar");
    if (appJar == null) {
      throw new UnsupportedOperationException(
          "expected command-line to include -appJar");
    }
    
    String exclusionFile = p.getProperty("exclusionFile", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    Warnings.clear();
    
    File tmp;
    if (exclusionFile != null) {
      tmp = new FileProvider().getFile(exclusionFile); 
    } else {
      tmp = new File(CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    }

    boolean debugTime = Boolean.parseBoolean(p.getProperty("measureTime"));

    if (debugTime) {
      timer.start();
    }
    // very important: define scope of analysis!
    // + relevant: what is in appJar and Java libraries
    // - not relevant: what is in exclusionFile
    
    scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, tmp);
    if (debugTime) {
      timer.stop();
      System.out.println("<< timing");
      timer.show("done building scope");
    }

    // Class Hierarchy Analysis (CHA)
    cha = ClassHierarchy.make(scope);
    options = new AnalysisOptions(scope, null);
    cache = new AnalysisCache();
    if (debugTime) {
      timer.stop();
      timer.show("done building CHA");
    }
  }

  /**
   * This method navigates through each and every method in the scope of
   * analysis (see scope) and builds a RWSet object for that method. It uses CHA
   * to perform the navigation.
   */
  private void directRWSets() {
    // finds rw-sets for all application methods
    List<IClass> toVisit = new ArrayList<IClass>();
    IClass root = getCHA().getRootClass();
    toVisit.add(root);
    Set<IMethod> visited = new HashSet<IMethod>();
    while (!toVisit.isEmpty()) {
      IClass cur = toVisit.remove(0);
      // want to visit all classes regardless it is application
      // as not application classes can be parent of application
      // classes.
      toVisit.addAll(getCHA().getImmediateSubclasses(cur));
      if (Util.isAppClass(cur)) {
        for (IMethod imethod : cur.getAllMethods()) {
          if (imethod.isNative() || imethod.isAbstract()
              || visited.contains(imethod)) {
            continue;
          }
          visited.add(imethod);
          updateRWSet(imethod);
        }
      }
    }
  }

  /***
   * This method builds a RWSet for a given method. It does **not** consider
   * indirect field reads and writes through other (say, private) methods
   **/
  private void updateRWSet(IMethod imethod) {
    // cache results for this call
    RWSet result = rwSets.get(imethod);
    if (result != null) {
      return;
    }

    IR ir = cache.getIRFactory().makeIR(imethod, Everywhere.EVERYWHERE,
        options.getSSAOptions());

    Set<AccessInfo> readSet = new HashSet<AccessInfo>();
    Set<AccessInfo> writeSet = new HashSet<AccessInfo>();
    Map<Integer, FieldReference> ssaVar = new HashMap<Integer, FieldReference>();

    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      SSAInstruction ins = instructions[i];
      if (ins == null)
        continue;
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
      case -1:
        break;
      case 0:
      case 1:
        SSAFieldAccessInstruction fai = (SSAFieldAccessInstruction) ins;
        // A FieldReference object denotes an access (read or write) to a field
        FieldReference fr = fai.getDeclaredField();
        Set<AccessInfo> set = (kind == 0) ? readSet : writeSet;
        IBytecodeMethod method = (IBytecodeMethod) ir.getMethod();
        try {
          int sourceLineNum = method.getLineNumber(method.getBytecodeIndex(i));
          
          // access field
          IClass iclass = getCHA().lookupClass(fr.getDeclaringClass());
          IField ifield = iclass.getField(fr.getName());
          
          AccessInfo accessInfo = RWSet.makeAccessInfo(method, sourceLineNum, ifield);
          set.add(accessInfo);
        } catch (InvalidClassFileException e) {
          e.printStackTrace();
        }
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
          IBytecodeMethod method1 = (IBytecodeMethod) ir.getMethod();
          try {
            int sourceLineNum = method1.getLineNumber(method1
                .getBytecodeIndex(i));
            
            // access field
            IClass iclass = getCHA().lookupClass(fr.getDeclaringClass());
            IField ifield = iclass.getField(fr.getName());
            
            AccessInfo accessInfo = RWSet.makeAccessInfo(method1, sourceLineNum, ifield);
            set.add(accessInfo);
          } catch (InvalidClassFileException e) {
            e.printStackTrace();
          }
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
   * This method propagates information of RWSet from callees to callers. It
   * needs call-graph information to identify the callees of a method.
   * 
   * This propagation is important since otherwise the impact of the call of the
   * private method may not be observable.
   * 
   * @param appJar
   * @param exclusionFile
   * @throws IllegalArgumentException
   * @throws WalaException
   * @throws CancelException
   * @throws IOException
   **/
  private void propagateRWSets(Graph<CGNode> callGraph)
      throws IllegalArgumentException, WalaException, CancelException,
      IOException {
    /**
     * Propagate information across the edges of the control-flow graph. For
     * that, we use the reverse topological order so to reduce the number of
     * iterations we need to process for computing fix point.
     */
    Iterator<CGNode> it = ReverseIterator.reverse(Topological
        .makeTopologicalIter(callGraph));
    while (it.hasNext()) {
      CGNode node = it.next();
      IMethod cMethod = node.getMethod();
      if (!Util.isRelevantMethod(cMethod)) {
        continue;
      }
      RWSet rwSetC = null;
      Iterator<CGNode> it2 = callGraph.getPredNodes(node);
      while (it2.hasNext()) {
        CGNode p = it2.next();
        IMethod pMethod = p.getMethod();
        if (!Util.isRelevantMethod(pMethod)) {
          continue;
        }
        RWSet rwSetP = rwSets.get(pMethod);
        if (rwSetP == null) {
          Util.logWarning("no RW-set info for method " + pMethod.toString());
          continue;
        }
        if (rwSetC == null) {
          rwSetC = rwSets.get(cMethod);
          if (rwSetC == null) {
            Util.logWarning("no RW-set info for method " + cMethod);
            continue;
          }
        }
        // propagate!
        rwSetP.writeSet.addAll(rwSetC.writeSet);
        rwSetP.readSet.addAll(rwSetC.readSet);
      }
    }
  }

  public SimpleGraph getDependencies(IMethod method, boolean onlyPublicClasses,
      boolean onlyPublicMethods, int strLine) throws CallGraphBuilderCancelException, ClassHierarchyException, IOException {
    if (method == null) {
      throw new RuntimeException("Could not find informed method!");
    }

    SimpleGraph result = new SimpleGraph();
    /********* find transitive method writers *********/
    Set<AccessInfo> reads = rwSets.get(method).readSet;

    if (strLine == -1) {
      findDependency(method, result, reads);
    } else {
      CallGraph cg = null;
      cg = this.getCallGraphGenerator().getFullCallGraph();
      Set<AccessInfo> flowingReads = findFlowingReadSet(method, strLine, cg);
      findDependency(method, result, flowingReads);
    }
    return result;
  }

  private void findDependency(IMethod method, SimpleGraph result,
      Set<AccessInfo> reads) {
    boolean onlyPublicClasses = false;
    boolean onlyPublicMethods = false;
    for (AccessInfo access : reads) {
      fillGraph(method, result, onlyPublicClasses, onlyPublicMethods, access);
    }
  }

  private void fillGraph(
      IMethod method, 
      SimpleGraph result,
      boolean onlyPublicClasses, 
      boolean onlyPublicMethods, 
      AccessInfo readAccessInfo) {
    for (Map.Entry<IMethod, RWSet> entry : rwSets.entrySet()) {
      IMethod writer = entry.getKey();
      if (onlyPublicClasses && !writer.getDeclaringClass().isPublic()) {
        continue;
      }
      if (onlyPublicMethods && !writer.isPublic()) {
        continue;
      }
      Set<AccessInfo> writeSet = entry.getValue().writeSet;
      for (AccessInfo writeAccessInfo : writeSet) {
        if (writeAccessInfo.iField.equals(readAccessInfo.iField)) {
          //result.getNode(writer).add(new Edge(method, fr, writeAccessInfo.accessLineNumber));
          Edge edge = 
              new Edge(writeAccessInfo.accessMethod, writeAccessInfo.accessLineNumber,
                    readAccessInfo.accessMethod, readAccessInfo.accessLineNumber, 
                    writeAccessInfo.iField);
          result.add(edge);
        }
      }
    }
  }

  public ClassHierarchy getCHA() {
    return cha;
  }

  public CallGraphGenerator getCallGraphGenerator() throws ClassHierarchyException, IOException{
    if(this.cgGenerator == null){
      this.cgGenerator = new CallGraphGenerator(this.scope, this.getCHA());
    }
    return this.cgGenerator;
  }

  /**
   * Finds the read set that flows to line <code>sourceLine</code> in method <code>method</code>.
   * @param method target method that contains the source line to find the flowing read set
   * @param sourceLine
   * @param cg the callgraph used to build the control flow graph
   * @return
   */
  private Set<AccessInfo> findFlowingReadSet(IMethod method, int sourceLine, CallGraph cg){
    Set<AccessInfo> reads = new HashSet<AccessInfo>();
    AbstractInterproceduralCFG<ISSABasicBlock> interproceduralCFG = new InterproceduralCFG(cg);

    // I am not 100% sure but I believe a single source line may encompass more than one basic block!
    List<ISSABasicBlock> initialBlocks = this.getBasicBlocksForSourceLine(method, sourceLine);

    /* Accounting for all possible CFGs of a given method.
     * The loop below is just a generalization in case we want to use a more precise callgraph
     * in the future.
     * Right now it should iterate only once as we are using a simple 0-CFA callgraph building algorithm
     * and as such there should be only one CGNode for each method.
     */
    Set<CGNode> cgNodes = cg.getNodes(method.getReference());
    for (CGNode cgNode : cgNodes) { 
      ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg = interproceduralCFG.getCFG(cgNode);
      Queue<ISSABasicBlock> blocksWorkList = new LinkedList<ISSABasicBlock>(initialBlocks);
      List<ISSABasicBlock> visitedBlocks = new ArrayList<ISSABasicBlock>();
      while(!blocksWorkList.isEmpty()){
        ISSABasicBlock block = blocksWorkList.poll();
        visitBlock(block, cgNode, cg, reads);
        visitedBlocks.add(block);
        addPredecessorsToWorkList(cfg, blocksWorkList, visitedBlocks, block);
      }
    }

    IR ir = cache.getIR(method);
    DefUse defUse = cache.getDefUse(ir);
    Queue<SSAInstruction> instructionsWorklist = getInitialInstructions(initialBlocks);
    Set<SSAInstruction> visitedInstructions = new HashSet<SSAInstruction>();
    while(!instructionsWorklist.isEmpty()) {
      SSAInstruction ssaInstruction = instructionsWorklist.poll();
      visitInstruction(ssaInstruction, ir, reads);
      visitedInstructions.add(ssaInstruction);
      addDefiningInstructionsToWorkList(ssaInstruction, defUse, instructionsWorklist,
                                          visitedInstructions);
    }
    return reads;
  }

  /**
   * Adds all instructions that define variables used in <code>ssaInstruction</code> to
   * <code>instructionsWorklist</code> if they are not contained in <code>visitedInstructions</code>.
   * @param ssaInstruction
   * @param defUse
   * @param instructionsWorklist
   * @param visitedInstructions
   */
  private void addDefiningInstructionsToWorkList(SSAInstruction ssaInstruction,
      DefUse defUse, Queue<SSAInstruction> instructionsWorklist, Set<SSAInstruction> visitedInstructions) {
    int numberOfUses = ssaInstruction.getNumberOfUses();
    for (int i = 0; i < numberOfUses; i++) {
      int use = ssaInstruction.getUse(i);
      SSAInstruction definingInstruction = defUse.getDef(use);
      if(definingInstruction != null && !visitedInstructions.contains(definingInstruction)){
        instructionsWorklist.add(definingInstruction);
      }
    }
  }

  private void visitInstruction(SSAInstruction ssaInstruction, IR ir, Set<AccessInfo> reads) {
    RWSet methodRWSet = rwSets.get(ir.getMethod());
    Set<AccessInfo> readSet = methodRWSet.readSet;
    if(ssaInstruction instanceof SSAGetInstruction){
      SSAGetInstruction getInstruction = (SSAGetInstruction) ssaInstruction;
      FieldReference fieldReference = getInstruction.getDeclaredField();
      IClass iClass = getCHA().lookupClass(fieldReference.getDeclaringClass());
      IField iField = iClass.getField(fieldReference.getName());
      for (AccessInfo readAccessInfo : readSet) {
        int sourceLine = getSourceLine((IBytecodeMethod) ir.getMethod(), getInstructionIndex(ssaInstruction, ir));
        if(sourceLine == readAccessInfo.accessLineNumber && readAccessInfo.iField.equals(iField)){
          reads.add(readAccessInfo);
        }
      }
    }
    
  }

  private Queue<SSAInstruction> getInitialInstructions(
      List<ISSABasicBlock> initialBlocks) {
    Queue<SSAInstruction> instructionsWorklist = new LinkedList<SSAInstruction>();
    for (ISSABasicBlock block : initialBlocks) {
      List<SSAInstruction> instructions = this.getInstructionsForBlock(block);
      instructions.removeAll(instructionsWorklist);
      instructionsWorklist.addAll(instructions);
    }
    return instructionsWorklist;
  }

  private void visitBlock(ISSABasicBlock block, CGNode methodNode, CallGraph cg, Set<AccessInfo> reads) {
    Set<IMethod> methods = this.rwSets.keySet();
    Iterator<SSAInstruction> blockIterator = block.iterator();
    while (blockIterator.hasNext()) {
      SSAInstruction ssaInstruction = (SSAInstruction) blockIterator.next();
      if(ssaInstruction == null){
        continue;
      }
      if(ssaInstruction instanceof SSAInvokeInstruction){
        SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction) ssaInstruction;
        for (CGNode possibleTarget : cg.getPossibleTargets(methodNode, invokeInstruction.getCallSite())) {
          if (methods.contains(possibleTarget.getMethod())) {
            reads.addAll(rwSets.get(possibleTarget.getMethod()).readSet);
          }
        }
      }
    }
  }

  /**
   * Add all predecessor basic blocks of <code>block</code> to <code>blocksWorkList</code> that
   * haven't been visited before.
   * @param cfg the control flow graph used to retrieve the predecessor blocks of <code>block</code>
   * @param blocksWorkList queue of basic blocks to which the predecessors may be added
   * @param visitedBlocks list of basic blocks that have already been visited and that should not
   * be added to <code>blocksWorkList</code> again
   * @param block
   */
  private void addPredecessorsToWorkList(
      ControlFlowGraph<SSAInstruction, ISSABasicBlock> cfg,
      Queue<ISSABasicBlock> blocksWorkList, List<ISSABasicBlock> visitedBlocks,
      ISSABasicBlock block) {
    Iterator<ISSABasicBlock> predNodes = cfg.getPredNodes(block);
    while (predNodes.hasNext()) {
      ISSABasicBlock issaBasicBlock = predNodes.next();
      if(!visitedBlocks.contains(issaBasicBlock)){
        blocksWorkList.add(issaBasicBlock);
      }
    }
  }

  /**
   * Returns the SSA basic blocks that make up source line <code>sourceLine</code>.
   * @param method the method that contains the source line indicated by <code>sourceLine</code>,
   * it <b>must</b> also be an instance of {@link IBytecodeMethod}
   * @param sourceLine the source line to get the basic blocks for
   * @return a list containing the basic blocks that may compose <code>sourceLine</code>
   */
  private List<ISSABasicBlock> getBasicBlocksForSourceLine(IMethod method, int sourceLine){
    List<ISSABasicBlock> ssaBasicBlocks = new ArrayList<ISSABasicBlock>();
    IR ir = cache.getIRFactory().makeIR(method, Everywhere.EVERYWHERE, options.getSSAOptions());
    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      SSAInstruction ssaInstruction = instructions[i];
      if(ssaInstruction == null){
        continue;
      }
      if(getSourceLine((IBytecodeMethod) method, i) == sourceLine){
        ISSABasicBlock basicBlock = ir.getBasicBlockForInstruction(ssaInstruction);
        if(!ssaBasicBlocks.contains(basicBlock)){
          ssaBasicBlocks.add(basicBlock);
        }
      }
    }
    return ssaBasicBlocks;
  }

  /**
   * Returns the source line for the instruction at index <code>instructionIndex</code> in method
   * <code>bMethod</code>.
   * If unable to determine the source line number this method returns -1. 
   * @param bMethod method to determine the line number
   * @param instructionIndex
   * @return the source line number for instruction at index <code>instructionIndex</code> in method
   * <code>bMethod</code> or -1 if unable to determine the source line number.
   */
  private int getSourceLine(IBytecodeMethod bMethod, int instructionIndex){
    int sourceLineNum = -1;
    try {
      sourceLineNum = bMethod.getLineNumber(bMethod.getBytecodeIndex(instructionIndex));
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
    }
    return sourceLineNum;
  }

  /**
   * Returns a list containing all the instructions in <code>block</code>.
   * <code>null</code> values are NOT included in the returned list.
   * The instructions order is the same as the one in <code>block</code>.
   * @param block
   * @return
   */
  private List<SSAInstruction> getInstructionsForBlock(ISSABasicBlock block){
    List<SSAInstruction> instructions = new ArrayList<SSAInstruction>();
    Iterator<SSAInstruction> blockIterator = block.iterator();
    while (blockIterator.hasNext()) {
      SSAInstruction ssaInstruction = (SSAInstruction) blockIterator.next();
      if(ssaInstruction != null){
        instructions.add(ssaInstruction);
      }
     }
    return instructions;
  }

  /**
   * Returns the instruction index for a given <code>instruction</code> contained in IR <code>ir</code>.
   * @param instruction
   * @param ir
   * @return
   */
  private int getInstructionIndex(SSAInstruction instruction, IR ir){
    int instructionIndex = -1;
    SSAInstruction[] instructions = ir.getInstructions();
    for (int i = 0; i < instructions.length && instructionIndex == -1; i++) {
      SSAInstruction ssaInstruction = instructions[i];
      if((ssaInstruction != null && ssaInstruction.equals(instruction)) ||
          ssaInstruction == instruction){
        instructionIndex = i;
      }
    }
    return instructionIndex;
  }
}