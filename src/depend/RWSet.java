package depend;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.types.FieldReference;

/****
 * 
 * auxiliary class denoting a pair of sets (of field references):
 * one to characterize the field reads of one method and
 * another to characterize the field writes of one method
 * 
 * @author damorim
 *
 ***/
public class RWSet {
  
  protected Set<FieldReference> readSet, writeSet;
  
  public RWSet(Set<FieldReference> readSet, Set<FieldReference> writeSet) {
    super();
    this.readSet = readSet;
    this.writeSet = writeSet;
  }

  /**
   * Returns a new <code>RWSet</code> by merging this readSet/writeSet with <code>other</code>'s readSet/writeSet.
   * Changes in the source <code>RWSet</code>s won't affect the returned set or vice-versa.
   * @param other the other <code>RWSet</code> to merge with this
   * @return a new RWSet representing the union of this' readSet/writeSet and <code>other</code>'s readSet/writeSet  
   */
  public RWSet merge(RWSet other){
    // TODO: Deal with null readSet/writeSets? Or we are assume they can't be null?
    Set<FieldReference> mergedReadSet = new HashSet<FieldReference>(this.readSet);
    mergedReadSet.addAll(other.readSet);
    Set<FieldReference> mergedWriteSet = new HashSet<FieldReference>(this.writeSet);
    mergedWriteSet.addAll(other.writeSet);
    return new RWSet(mergedReadSet, mergedWriteSet);
  }
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("READS FROM:");
    sb.append("\n");
    for (FieldReference fr : readSet) {
      sb.append(" " + fr);
      sb.append("\n");
    }
    System.out.println("WRITES TO:");
    for (FieldReference fr : writeSet) {
      sb.append(" " + fr);
      sb.append("\n");
    }
    return sb.toString();
  }
}