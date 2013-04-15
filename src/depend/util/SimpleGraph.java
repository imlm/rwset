package depend.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.FieldReference;

public class SimpleGraph {

  private static class Node {
    IMethod imethod;
  }

  private static class Edge {
    FieldReference fr;
    int line;
    Node node;
  }

  private Map<Node, Set<Edge>> edges = new HashMap<Node, Set<Edge>>(); 

  public Set<Edge> getNode(IMethod meth) {
    Set<Edge> result = edges.get(meth);
    if (result == null) {
      result = new HashSet<Edge>();
    }
    return result;
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("digraph \"DirectedGraph\" {\n");
    sb.append(" graph [concentrate = true];\n");
    sb.append(" center=true;\n");
    sb.append(" fontsize=6;\n");
    sb.append(" node [ color=blue,shape=\"box\"fontsize=6,fontcolor=black,fontname=Arial];\n");
    sb.append(" edge [ color=black,fontsize=6,fontcolor=black,fontname=Arial];\n");    
    for (Entry<Node, Set<Edge>> entry: edges.entrySet()) {
      IMethod source = entry.getKey().imethod;
      if (Util.isAppClass(source.getDeclaringClass())) {
        for (Edge edge : entry.getValue()) {
          sb.append(source);
          sb.append(" -> ");
          sb.append(edge.node.imethod);
          sb.append(" [");
          sb.append("label=\"");
          sb.append(edge.fr);
          sb.append(" : ");
          sb.append(edge.line);
          sb.append("\"");
          sb.append(" ]");
          sb.append("\n");
        }
      }      
    }
    sb.append("}\n");
    return sb.toString();
  }

}
