package depend.util.graph;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

public class SimpleGraph {

//  private Map<IMethod, Set<Edge>> edges = new HashMap<IMethod, Set<Edge>>();
  
  private Set<Edge> edges = new HashSet<Edge>();

//  public Set<Edge> getNode(IMethod meth) {
//    Set<Edge> result = edges.get(meth);
//    if (result == null) {
//      result = new HashSet<Edge>();
//      edges.put(meth, result);
//    }
//    return result;
//  }
  
  public String toDotString() {
    StringBuffer sb = new StringBuffer();
    sb.append("digraph \"DirectedGraph\" {\n");
    sb.append(" graph [concentrate = true];\n");
    sb.append(" center=true;\n");
    sb.append(" fontsize=6;\n");
    sb.append(" node [ color=blue,shape=\"box\"fontsize=6,fontcolor=black,fontname=Arial];\n");
    sb.append(" edge [ color=black,fontsize=6,fontcolor=black,fontname=Arial];\n");    
    sb.append(toString());
    sb.append("}\n");
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
//    for (Entry<IMethod, Set<Edge>> entry: edges.entrySet()) {
    for (Edge edge : edges) {
      sb.append("\"");
      sb.append(toString(edge.writer));
      sb.append("\"");

      sb.append(" -> ");

      sb.append("\"");
      sb.append(toString(edge.reader));
      sb.append("\"");

      sb.append(" [");
      sb.append("label=\"");

      //          sb.append(edge.fr);
      sb.append(toString(edge.ifield.getFieldTypeReference().getName()));
      sb.append(" ");
      sb.append(toString(edge.ifield.getDeclaringClass().getName()));
      sb.append(".");
      sb.append(edge.ifield.getName());
      sb.append(" : ");
      sb.append(edge.writerLine);

      sb.append("\"");
      sb.append(" ]");
      sb.append("\n");
    }
    return sb.toString();
  }
  
  private Object toString(IMethod meth) {
    StringBuffer sb = new StringBuffer();
    Descriptor desc = meth.getDescriptor();
    sb.append(toString(desc.getReturnType()));
    sb.append(" ");
    sb.append(toString(meth.getDeclaringClass().getReference().getName()));
    sb.append(".");
    sb.append(meth.getName());
    sb.append("(");
    TypeName[] paramTypes = desc.getParameters(); 
    for (int i = 0 ; i < desc.getNumberOfParameters(); i++) {
      TypeName tn = paramTypes[i];
      sb.append(toString(tn));
      if (i < desc.getNumberOfParameters() - 1) {
        sb.append(",");
      }
    }
    sb.append(")");    
    return sb.toString();
  }

  private String toString(TypeName tn) {
    String result;
    if (tn.isPrimitiveType()) {
      if (tn == TypeReference.BooleanName) {
        result = "boolean";
      } else if (tn == TypeReference.ByteName) {
        result = "byte";
      } else if (tn == TypeReference.CharName) {
        result = "char";
      } else if (tn == TypeReference.DoubleName) {
        result = "double";
      } else if (tn == TypeReference.FloatName) {
        result = "float";
      } else if (tn == TypeReference.IntName) {
        result = "int";
      } else if (tn == TypeReference.LongName) {
        result = "long";
      } else if (tn == TypeReference.ShortName) {
        result = "short";
      } else if (tn == TypeReference.VoidName) {
        result = "void";
      } else {
        throw new UnsupportedOperationException();
      }
    } else {
      result = tn.toString().substring(1);
    } 
    return result;
  }

  public void add(Edge edge) {
    edges.add(edge);
  }

}