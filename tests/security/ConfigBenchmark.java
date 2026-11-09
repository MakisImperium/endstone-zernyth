import dev.zernyth.api.config.Configuration;
import java.util.*;
public class ConfigBenchmark {
 static volatile long sink;
 public static void main(String[] args) {
  var seed=new LinkedHashMap<String,Object>();for(int i=0;i<100;i++) seed.put("key"+i,i);
  var config=new Configuration(seed);long[] samples=new long[9];
  for(int round=-3;round<9;round++) {
   long start=System.nanoTime();for(int i=0;i<1000;i++) config.set("counter",i);
   long elapsed=System.nanoTime()-start;if(round>=0)samples[round]=elapsed/1000;sink=config.getInt("counter",0);
  }
  Arrays.sort(samples);System.out.println("100-property set wall ns/op median="+samples[4]+" maxBatch="+samples[8]+" samples="+Arrays.toString(samples));
 }
}