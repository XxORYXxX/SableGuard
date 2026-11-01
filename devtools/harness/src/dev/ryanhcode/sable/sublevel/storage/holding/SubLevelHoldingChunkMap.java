package dev.ryanhcode.sable.sublevel.storage.holding;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
public class SubLevelHoldingChunkMap implements dev.sableguard.mixin.accessor.HoldingChunkMapAccessor, dev.sableguard.core.GuardStateHolder {
 public net.minecraft.server.level.ServerLevel level; public dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container; public final dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage storage=new dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage();
 public final it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<java.util.UUID, HoldingSubLevel> all=new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();
 public final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<SubLevelHoldingChunk> loaded=new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
 public final it.unimi.dsi.fastutil.objects.ObjectSetImpl<net.minecraft.world.level.ChunkPos> queued=new it.unimi.dsi.fastutil.objects.ObjectSetImpl<>();
 public final java.util.Set<net.minecraft.world.level.ChunkPos> dirty=new java.util.HashSet<>(); dev.sableguard.core.GuardState st; public final java.util.Set<java.util.UUID> failLoad=new java.util.HashSet<>();
 public void saveAll(){} public dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage getStorage(){return storage;}
 public net.minecraft.server.level.ServerLevel sableguard$level(){return level;} public dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer sableguard$container(){return container;}
 public it.unimi.dsi.fastutil.objects.Object2ObjectMap<java.util.UUID, HoldingSubLevel> sableguard$allHoldingSubLevels(){return all;} public it.unimi.dsi.fastutil.longs.Long2ObjectMap<SubLevelHoldingChunk> sableguard$loadedHoldingChunks(){return loaded;}
 public it.unimi.dsi.fastutil.objects.ObjectSet<net.minecraft.world.level.ChunkPos> sableguard$queuedUnloads(){return queued;} public void sableguard$setDirty(net.minecraft.world.level.ChunkPos p){dirty.add(p);}
 /** mirrors sable 2.0.6 getOrLoadHoldingChunk(pos, false) */
 public SubLevelHoldingChunk sableguard$getOrLoadHoldingChunk(net.minecraft.world.level.ChunkPos pos, boolean create){ var c=loaded.get(pos.toLong()); if(c!=null) return c; var disk=storage.attemptLoadHoldingChunk(pos);
   if(disk!=null){ for(var ptr: disk.pointers){ var data=storage.attemptLoadSubLevel(pos, ptr); if(data==null || failLoad.contains(data.uuid())) continue; var h=new HoldingSubLevel(data,new GlobalSavedSubLevelPointer(pos,ptr.storageIndex(),ptr.subLevelIndex())); disk.held.put(data.uuid(),h); all.put(data.uuid(),h);} loaded.put(pos.toLong(),disk); return disk; }
   if(create){ var n=new SubLevelHoldingChunk(pos); loaded.put(pos.toLong(),n); return n;} return null; }
 public dev.sableguard.core.GuardState sableguard$getState(){ if(st==null) st=new dev.sableguard.core.GuardState(this); return st; } }
