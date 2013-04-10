package examples;

public class A {
  
  int x;
  int y;
  
  void m() {
    x = 10;
    l();
  }
  
  void l() {
    x++;  // B.n() will not be dependent on A.l() if you comment this line 
    o();
  }
  
  void o() {
    if (x > 20) {
      y = 2;
    }
  }

}
