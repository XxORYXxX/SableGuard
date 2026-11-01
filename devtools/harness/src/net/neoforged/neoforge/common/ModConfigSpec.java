package net.neoforged.neoforge.common; public class ModConfigSpec implements net.neoforged.fml.config.IConfigSpec {
 public static class ConfigValue<T> { public T get(){ throw new IllegalStateException("not loaded"); } }
 public static class BooleanValue extends ConfigValue<Boolean> {} public static class IntValue extends ConfigValue<Integer> {} public static class DoubleValue extends ConfigValue<Double> {}
 public static class Builder { public Builder comment(String... c){return this;} public Builder push(String p){return this;} public Builder pop(){return this;}
  public BooleanValue define(String p, boolean d){return new BooleanValue();} public IntValue defineInRange(String p,int d,int a,int b){return new IntValue();} public DoubleValue defineInRange(String p,double d,double a,double b){return new DoubleValue();} public ModConfigSpec build(){return new ModConfigSpec();} } }
