package core;

import japa.parser.ParseException;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import depend.util.graph.SimpleGraph;

public class Sanity {
  
  String USER_DIR = System.getProperty("user.dir");
  String SEP = System.getProperty("file.separator");
  
  @Test
  public void test0() throws IOException, WalaException, CancelException, ParseException, InvalidClassFileException {

    String strCompUnit = USER_DIR + SEP + "src-examples/foo/D.java";
    
    Assert.assertTrue((new File(strCompUnit)).exists());
    
    String line = "System.out.println(\"hello\");";    

    // checking whether it will raise an exception
    depend.Main.analyze("foo.jar", "foo", strCompUnit, line);
  }
  
  @Test
  public void test1() throws IOException, WalaException, CancelException, ParseException, InvalidClassFileException {

    String strCompUnit = USER_DIR + SEP + "src-examples/foo/B.java";
    
    Assert.assertTrue((new File(strCompUnit)).exists());
    
    String line = "a.y + a.z > c.y + w";    

    // checking whether it will raise an exception
    SimpleGraph sg = depend.Main.analyze("foo.jar", "foo", strCompUnit, line);
        
    String expectedResultFile = USER_DIR + SEP + "src-tests/core/Sanity.test1.data";

    String expected = Helper.readFile(expectedResultFile);
        
    Assert.assertEquals(expected, sg.toDotString());
  }
  

}
