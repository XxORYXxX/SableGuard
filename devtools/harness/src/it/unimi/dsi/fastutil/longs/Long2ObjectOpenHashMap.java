package it.unimi.dsi.fastutil.longs; public class Long2ObjectOpenHashMap<V> implements Long2ObjectMap<V> { public final java.util.LinkedHashMap<Long,V> m=new java.util.LinkedHashMap<>();
 public V get(long k){return m.get(k);} public boolean containsKey(long k){return m.containsKey(k);} public boolean isEmpty(){return m.isEmpty();} public int size(){return m.size();} public V put(long k,V v){return m.put(k,v);}
 public it.unimi.dsi.fastutil.objects.ObjectCollection<V> values(){ return new it.unimi.dsi.fastutil.objects.ObjectArrayList<V>(m.values()); } }
