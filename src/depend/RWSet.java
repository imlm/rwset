package depend;

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