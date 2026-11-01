package dev.ryanhcode.sable.sublevel.storage.serialization; public class SubLevelStorage { public java.nio.file.Path folder;
 /** fake disk: chunk -> (pointers, data per pointer) */ public final java.util.Map<Long, java.util.Map<dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer, SubLevelData>> disk=new java.util.HashMap<>();
 public java.nio.file.Path getFolder(){return folder;} public void flush(){}
 public dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk attemptLoadHoldingChunk(net.minecraft.world.level.ChunkPos p){ var d=disk.get(p.toLong()); if(d==null) return null; var c=new dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk(p); c.pointers.addAll(d.keySet()); return c; }
 public SubLevelData attemptLoadSubLevel(net.minecraft.world.level.ChunkPos p, dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer ptr){ var d=disk.get(p.toLong()); return d==null?null:d.get(ptr); }
 public void attemptSaveSubLevel(dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer p, SubLevelData d){} }
