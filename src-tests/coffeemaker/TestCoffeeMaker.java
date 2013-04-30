package coffeemaker;

import japa.parser.ParseException;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import core.Helper;
import depend.util.graph.SimpleGraph;

public class TestCoffeeMaker {
  
  String USER_DIR = System.getProperty("user.dir");
  String SEP = System.getProperty("file.separator");

  @Test
  public void test0() throws IOException, WalaException, CancelException, ParseException, InvalidClassFileException {

    String strCompUnit = USER_DIR + SEP + "src-examples/coffeemaker/CoffeeMaker.java";
    
    Assert.assertTrue((new File(strCompUnit)).exists());
    
    String line = "if(addRecipe(newRecipe)) {";
    
    String coffeejar = USER_DIR + SEP + "coffee.jar";
    
    // check
    Assert.assertTrue((new File(strCompUnit)).exists());

    SimpleGraph sg = depend.Main.analyze(coffeejar, "coffee", strCompUnit, line);
        
    String expectedResultFile = USER_DIR + SEP + "src-tests/coffeemaker/TestCoffeeMaker.test0.data";
    
    // check
    Assert.assertEquals(Helper.readFile(expectedResultFile), sg.toDotString());
  }

 

}
