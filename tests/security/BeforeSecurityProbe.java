package dev.zernyth.runtime;
import dev.zernyth.api.config.Configuration;
import java.util.*;
public class BeforeSecurityProbe {
 public static void main(String[] args) throws Exception {
  Codecs.parse("{\"nested\":{\"x\":1,\"x\":2}}",true);
  System.out.println("0.7 confirmed: nested duplicate JSON accepted");
  var c=new Configuration(Map.of("stable",1));var before=c.values();
  try { c.set("created.section.value",new Object()); } catch(IllegalArgumentException expected) {}
  if(c.values().equals(before)) throw new AssertionError("Baseline differs");
  System.out.println("0.7 confirmed: rejected mutation leaves sections behind");
  var expanded=new LinkedHashMap<String,Object>();for(int i=0;i<100;i++) expanded.put("k"+i,Collections.nCopies(2000,1));
  new Configuration(expanded);System.out.println("0.7 confirmed: 200000-node shared input expands without aggregate limit");
  Codecs.parse("{\"x\":\"\\ud800\"}",true);System.out.println("0.7 confirmed: escaped unpaired surrogate accepted");
 }
}