package depend.util.parser;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.PackageDeclaration;
import japa.parser.ast.TypeParameter;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.ModifierSet;
import japa.parser.ast.type.ClassOrInterfaceType;
import japa.parser.ast.type.Type;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class Util {
  
  public static void main(String[] args) throws ParseException, IOException, ClassNotFoundException {
    String line="if (true) {";    
    String strCompUnit = 
        System.getProperty("user.dir") + 
        System.getProperty("file.separator") + 
        "src/examples/D.java";
    getLineAndWALAClassName(line, strCompUnit);
  }

  public static String[] getLineAndWALAClassName(String line, String strCompUnit) throws ParseException, IOException {
    File clazz = new File(strCompUnit);
    CompilationUnit cUnit = parserClass(clazz);    
    String str = cUnit.toString();
    StringReader sr = new StringReader(str);
    BufferedReader br = new BufferedReader(sr);
    String s;
    int i = 1;
    int found = -1;
    String lastClassStr = "";
    while ((s = br.readLine())!=null) {
      if (s.contains("class")) {
        lastClassStr = s;
      }
      if (s.contains(line)) {
        if (found != -1) {
          throw new RuntimeException("umbiguous string");
        }
        found = i;
      }
      i++;
    }
    if (found == -1) {
      throw new RuntimeException("could not find informed string in the compilation unit");
    }
    MyVisitorAdapter vva = new MyVisitorAdapter(lastClassStr, line);
    
    cUnit.accept(vva, null);
    
    if (!vva.found) {
      throw new RuntimeException();
    }
    
    String[] result = new String[] {
        found+"",
        to_WALA_ClassName(vva.pd, vva.stack)
    };
    
    return result;   
  }
  
  private static CompilationUnit parserClass(File clazz) throws ParseException {
    try {
      return JavaParser.parse(clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
 
//  private static String to_WALA_type(Type type) throws ClassNotFoundException {
//    StringBuffer sb = new StringBuffer();
//    if (type instanceof VoidType) {
//      sb.append("V");
//    } else if (type instanceof PrimitiveType) {
//      switch (((PrimitiveType)type).getType()) {
//      case Boolean:
//        sb.append("Z");
//        break;
//      case Char:
//        sb.append("C");
//        break;
//      case Byte:
//        sb.append("B");
//        break;
//      case Short:
//        sb.append("S");
//        break;
//      case Int:
//        sb.append("I");
//        break;
//      case Long:
//        sb.append("J");
//        break;
//      case Float:
//        sb.append("F");
//        break;
//      case Double:
//        sb.append("D");
//        break;
//      default:
//        break;
//      }
//    } else if (type instanceof ReferenceType) {
//      sb.append("L");
//      sb.append(mangleTypeName(type));
//      sb.append(";");      
//    } else if (type instanceof ClassOrInterfaceType) {
//      sb.append("L");
//      sb.append(mangleTypeName(type));
//      sb.append(";");      
//    } else {
//      throw new UnsupportedOperationException();
//    }
//    return sb.toString();
//  }
  
  static Set<String> javalang = new HashSet<String>();


  private static String to_WALA_ClassName(PackageDeclaration pd,
      Stack<ClassOrInterfaceDeclaration> stack) {
    StringBuffer sb = new StringBuffer();
    sb.append("L");
    if (pd != null) {
      sb.append(pd.getName());
      sb.append("/");
    }
    for (int i = 0; i < stack.size(); i++) {
      ClassOrInterfaceDeclaration cid = stack.get(i);
      sb.append(cid.getName());
      if (i < stack.size() - 1) {
        sb.append("$");
      }
    }
    return sb.toString();
  }

  static class MyVisitorAdapter extends VoidVisitorAdapter<Void> {
    
    private String cname, line;
        
    public MyVisitorAdapter(String cname, String line) {
      this.cname = cname;
      this.line = line;
    }

    PackageDeclaration pd;
    Stack<ClassOrInterfaceDeclaration> stack = new Stack<ClassOrInterfaceDeclaration>();
    MethodDeclaration md;
    private boolean found = false;

    
    @Override
    public void visit(PackageDeclaration n, Void arg) {
      super.visit(n, arg);
      pd = n;
    }
    
    @Override
    public void visit(MethodDeclaration n, Void arg) {
      super.visit(n, arg);
      if (n.getBody().toString().contains(line)) {
        if (md != null) {
          // defensive programming
          throw new RuntimeException("found another ocurrence of this line");
        } 
        md = n;  
      }
    }
    
    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
      if (!found) {
        stack.push(n);
      }
      StringBuffer sb = new StringBuffer();
      Util.visit(n, null, sb);
//      System.out.println("===");
//      System.out.println(sb.toString().trim());
//      System.out.println(name);
      if (sb.toString().trim().equals(cname.trim())) {
        found = true;
      }        
      super.visit(n, arg);
      if (!found) {
        stack.pop();
      }
    }
    
  };
  
  private static void visit(ClassOrInterfaceDeclaration n, Void arg, StringBuffer printer) {

    //      printJavadoc(n.getJavaDoc(), arg);
    //      printMemberAnnotations(n.getAnnotations(), arg);
    printModifiers(n.getModifiers(), printer);

    if (n.isInterface()) {
      printer.append("interface ");
    } else {
      printer.append("class ");
    }

    printer.append(n.getName());

    printTypeParameters(n.getTypeParameters(), arg, printer);

    if (n.getExtends() != null) {
      printer.append(" extends ");
      for (Iterator<ClassOrInterfaceType> i = n.getExtends().iterator(); i.hasNext();) {
        ClassOrInterfaceType c = i.next();
        visit(c, arg, printer);
        if (i.hasNext()) {
          printer.append(", ");
        }
      }
    }

    if (n.getImplements() != null) {
      printer.append(" implements ");
      for (Iterator<ClassOrInterfaceType> i = n.getImplements().iterator(); i.hasNext();) {
        ClassOrInterfaceType c = i.next();
        visit(c, arg, printer);
        if (i.hasNext()) {
          printer.append(", ");
        }
      }
    }

    printer.append(" {");
  }
  
  // sliced from javaparser
  private static void printModifiers(int modifiers, StringBuffer result) {
    if (ModifierSet.isPrivate(modifiers)) {
      result.append("private ");
    }
    if (ModifierSet.isProtected(modifiers)) {
      result.append("protected ");
    }
    if (ModifierSet.isPublic(modifiers)) {
      result.append("public ");
    }
    if (ModifierSet.isAbstract(modifiers)) {
      result.append("abstract ");
    }
    if (ModifierSet.isStatic(modifiers)) {
      result.append("static ");
    }
    if (ModifierSet.isFinal(modifiers)) {
      result.append("final ");
    }
    if (ModifierSet.isNative(modifiers)) {
      result.append("native ");
    }
    if (ModifierSet.isStrictfp(modifiers)) {
      result.append("strictfp ");
    }
    if (ModifierSet.isSynchronized(modifiers)) {
      result.append("synchronized ");
    }
    if (ModifierSet.isTransient(modifiers)) {
      result.append("transient ");
    }
    if (ModifierSet.isVolatile(modifiers)) {
      result.append("volatile ");
    }
  }

  private static void printTypeParameters(List<TypeParameter> args, Object arg, StringBuffer sb) {
    if (args != null) {
      sb.append("<");
      for (Iterator<TypeParameter> i = args.iterator(); i.hasNext();) {
        TypeParameter t = i.next();
        visit(t, arg, sb);
        if (i.hasNext()) {
          sb.append(", ");
        }
      }
      sb.append(">");
    }
  }
  
  private static void visit(TypeParameter n, Object arg, StringBuffer sb) {
    sb.append(n.getName());
    if (n.getTypeBound() != null) {
      sb.append(" extends ");
      for (Iterator<ClassOrInterfaceType> i = n.getTypeBound().iterator(); i.hasNext();) {
        ClassOrInterfaceType c = i.next();
        visit(c, arg, sb);
        if (i.hasNext()) {
          sb.append(" & ");
        }
      }
    }
  }
  
  public static void visit(ClassOrInterfaceType n, Object arg, StringBuffer printer) {
    if (n.getScope() != null) {
      visit(n.getScope(), arg, printer); // recursion
      printer.append(".");
    }
    printer.append(n.getName());
    printTypeArgs(n.getTypeArgs(), arg, printer);
  }
  
  private static void printTypeArgs(List<Type> args, Object arg, StringBuffer printer) {
    if (args != null) {
      printer.append("<");
      for (Iterator<Type> i = args.iterator(); i.hasNext();) {
        throw new UnsupportedOperationException("TODO");
//        Type t = i.next();
//        visit(t, arg, printer);
//        if (i.hasNext()) {
//          printer.append(", ");
//        }
      }
      printer.append(">");
    }
  }
  

}
