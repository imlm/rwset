package depend;

import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;

/**
 * This interface describe methods for defining mappings of source lines in methods to {@link RWSet}s.
 * <br>It also defines methods to query for <code>RWSet</code> information at different levels of granularity,
 * i.e., clients may ask for information at method level ({@link #get(IMethod)}) or at source line level ({@link #get(IMethod, int)}); 
 * <br> This interface also defines the {@link #UNKNOWN_LINE} constant which represents an unknown line number for the cases
 * when clients are unable to precisely determine the line number for a given <code>RWSet</code>.
 */
public interface RWMappings {

  /**
   * The line number to be used when unable to determine the source line number for a given <code>RWSet</code>.
   */
  int UNKNOWN_LINE = -1;

  /**
   * Maps <code>rwSet</code> at line <code>sourceLineNumber</code> for method <code>method</code>.
   * <br>If a mapping already existed it is replaced with the new value.  
   * @param method
   * @param sourceLineNumber
   * @param rwSet
   */
  void put(IMethod method, int sourceLineNumber, RWSet rwSet);

  /**
   * Maps <code>rwSet</code> at {@link #UNKNOWN_LINE} in method <code>method</code>.
   * <br>If a mapping already existed it is replaced with the new value.
   * @param method
   * @param rwSet
   */
  void put(IMethod method, RWSet rwSet);

  /**
   * Convenience method for updating a RWSet mapping with a new write set.
   * If no mapping existed a new one is created containing <code>writeRefs</code> as the write set and an 
   * empty set as the read set.
   * @param method
   * @param sourceLineNumber
   * @param writeRefs
   */
  void putOrMergeWrite(IMethod method, int sourceLineNumber, Set<FieldReference> writeRefs);

  /**
   * Convenience method for updating a RWSet mapping with a new read set.
   * If no mapping existed a new one is created containing <code>readRefs</code> as the read set and an 
   * empty set as the write set.
   * @param method
   * @param sourceLineNumber
   * @param readRefs
   */
  void putOrMergeRead(IMethod method, int sourceLineNumber, Set<FieldReference> readRefs);
  RWSet get(IMethod method, int sourceLineNumber);

  /**
   * Returns the complete <code>RWSet</code> for method <code>method</code>.
   * <br>The returned set represent the union among the sets for each line mapping in <code>method</code>.
   * <br>If there is no mapping for <code>method</code> <code>null</code> should be returned.
   * @param method
   * @return
   */
  RWSet get(IMethod method);

  /**
   * Returns the RWSet information at method granularity for each mapped method.
   * @return
   */
  Map<IMethod, RWSet> getAllMethodMappings();

  /**
   * Returns the RWSet information at source line granularity for each mapped method.
   * @return
   */
  Map<IMethod, Map<Integer,RWSet>> getAllLineMappings();

}
