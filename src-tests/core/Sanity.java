package core;

import japa.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import depend.util.SimpleGraph;

public class Sanity {
  
  String USER_DIR = System.getProperty("user.dir");
  String SEP = System.getProperty("file.separator");
  
  @Test
  public void test0() throws IOException, WalaException, CancelException, ParseException {

    String strCompUnit = USER_DIR + SEP + "src/examples/D.java";
    
    Assert.assertTrue((new File(strCompUnit)).exists());
    
    String line = "if (true) {";    

    // checking whether it will raise an exception
    depend.Main.analyze("examples.jar", "examples", strCompUnit, line);
  }
  
  @Test
  public void test1() throws IOException, WalaException, CancelException, ParseException {

    String strCompUnit = USER_DIR + SEP + "src/examples/B.java";
    
    Assert.assertTrue((new File(strCompUnit)).exists());
    
    String line = "a.y + a.z > c.y + w";    

    // checking whether it will raise an exception
    SimpleGraph sg = depend.Main.analyze("examples.jar", "examples", strCompUnit, line);
        
    String expectedResultFile = USER_DIR + SEP + "src-tests/core/Sanity.test1.data";
    
    Assert.assertEquals(readFile(expectedResultFile), sg.toDotString());
  }

  private String readFile(String fileName) throws FileNotFoundException, IOException {
    StringBuffer sb = new StringBuffer();
    FileReader fr = new FileReader(new File(fileName));
    BufferedReader br = new BufferedReader(fr);
    String tmp;
    while ((tmp = br.readLine())!=null) {
      sb.append(tmp);
      sb.append("\n");
    }
    br.close();
    fr.close();
    return sb.toString();
  }

}
