// "Wrap using 'Collections.singletonList()'" "true"
import java.util.*;

class Test {
  List<Throwable> test() {
    try {
      System.out.println();
    }
    catch (Exception | Error ex) {
      return Collections.singletonList(ex);
    }
    return null;
  }
}