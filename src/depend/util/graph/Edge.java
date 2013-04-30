package depend.util.graph;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;

public class Edge {

  // writer of data
  IMethod writer;
  int writerLine;
  
  // reader of data
  IMethod reader;
  int readerLine;
  
  // field (can be declared in a third method/class) 
  IField ifield;
  
  
  public Edge(
      IMethod writer, 
      int writerLine, 
      IMethod reader, 
      int readerLine,
      IField ifield) {
    super();
    this.writer = writer;
    this.writerLine = writerLine;
    this.reader = reader;
    this.readerLine = readerLine;
    this.ifield = ifield;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ifield == null) ? 0 : ifield.hashCode());
    result = prime * result + writerLine;
//    result = prime * result + readerLine;
    result = prime * result + ((writer == null) ? 0 : writer.hashCode());
    result = prime * result + ((reader == null) ? 0 : reader.hashCode());
    return result;
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Edge other = (Edge) obj;
    if (ifield == null) {
      if (other.ifield != null)
        return false;
    } else if (!ifield.equals(other.ifield))
      return false;
    if (writerLine != other.writerLine)
      return false;
//    if (readerLine != other.readerLine)
//      return false;
    if (writer == null) {
      if (other.writer != null)
        return false;
    } else if (!writer.equals(other.writer))
      return false;
    if (reader == null) {
      if (other.reader != null)
        return false;
    } else if (!reader.equals(other.reader))
      return false;
    return true;
  }
  
}