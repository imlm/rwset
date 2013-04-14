package depend;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;

/**
 * {@link RWMappings} that is backed up by a map of maps.
 *
 */
public class MultiLayerRWMappings implements RWMappings {

  private Map<IMethod, Map<Integer, RWSet>> methodToLinesToRWSetMappings;

  public MultiLayerRWMappings() {
    this.methodToLinesToRWSetMappings = new HashMap<IMethod, Map<Integer,RWSet>>();
  }

  @Override
  public void put(IMethod method, int sourceLineNumber, RWSet rwSet) {
     Map<Integer, RWSet> linesMapping;
     if((linesMapping = this.methodToLinesToRWSetMappings.get(method)) == null){
       linesMapping = new HashMap<Integer, RWSet>();
       this.methodToLinesToRWSetMappings.put(method, linesMapping);
     }
     linesMapping.put(sourceLineNumber, rwSet);
  }

  @Override
  public void putOrMergeWrite(IMethod method, int sourceLineNumber,
      Set<FieldReference> writeRefs) {
    RWSet rwSet = this.get(method, sourceLineNumber);
    if(rwSet != null){
      this.put(method, sourceLineNumber, rwSet.merge(new RWSet(Collections.<FieldReference>emptySet(), writeRefs)));
    } else {
      this.put(method, sourceLineNumber, new RWSet(Collections.<FieldReference>emptySet(), writeRefs));
    }
  }

  @Override
  public void putOrMergeRead(IMethod method, int sourceLineNumber,
      Set<FieldReference> readRefs) {
    RWSet rwSet = this.get(method, sourceLineNumber);
    if(rwSet != null){
      this.put(method, sourceLineNumber, rwSet.merge(new RWSet(readRefs, Collections.<FieldReference>emptySet())));
    } else {
      this.put(method, sourceLineNumber, new RWSet(readRefs, Collections.<FieldReference>emptySet()));
    }
  }

  @Override
  public void put(IMethod method, RWSet rwSet) {
    this.put(method, UNKNOWN_LINE, rwSet);
  }

  @Override
  public RWSet get(IMethod method, int sourceLineNumber) {
    RWSet rwSet = null;
    Map<Integer, RWSet> linesMapping;
    if((linesMapping = this.methodToLinesToRWSetMappings.get(method)) != null){
      rwSet = linesMapping.get(sourceLineNumber);
    }
    return rwSet;
  }

  @Override
  public RWSet get(IMethod method) {
    RWSet rwSet = null;
    Map<Integer, RWSet> linesMapping;
    if((linesMapping = this.methodToLinesToRWSetMappings.get(method)) != null){
      Set<Entry<Integer, RWSet>> allMappings = linesMapping.entrySet();
      for (Entry<Integer, RWSet> lineMapping : allMappings) {
        if(rwSet == null){
          rwSet = lineMapping.getValue();
        } else {
          rwSet = rwSet.merge(lineMapping.getValue());
        }
      }
    }
    return rwSet;
  }

  @Override
  public Map<IMethod, RWSet> getAllMethodMappings() {
    Map<IMethod, RWSet> allMappings = new HashMap<IMethod, RWSet>();
    Set<IMethod> keySet = this.methodToLinesToRWSetMappings.keySet();
    for (IMethod iMethod : keySet) {
      allMappings.put(iMethod, this.get(iMethod));
    }
    return allMappings;
  }

  @Override
  public Map<IMethod, Map<Integer, RWSet>> getAllLineMappings() {
    Map<IMethod, Map<Integer, RWSet>> allLineMappings = new HashMap<IMethod, Map<Integer,RWSet>>();
    Set<Entry<IMethod, Map<Integer, RWSet>>> entrySet = this.methodToLinesToRWSetMappings.entrySet();
    for (Entry<IMethod, Map<Integer, RWSet>> entry : entrySet) {
      allLineMappings.put(entry.getKey(), new HashMap<Integer, RWSet>(entry.getValue()));
    }
    return allLineMappings;
  }

  
}
