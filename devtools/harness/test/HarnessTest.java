import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.*;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.sableguard.core.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.*;
import java.util.*;

/**
 * Drives OrphanResolver the way SubLevelHoldingChunkMap.processChanges() does in Sable 2.0.6:
 *   resolver (our INVOKE-after-processUnloads hook) -> Sable's ready-check per non-queued chunk -> TAIL restore.
 * Sable's ready-check is reproduced from SubLevelHoldingChunk.collectReadySubLevels (2.0.6) so we can see whether
 * vanilla would have dropped the group ("Sub-level dependency does not exist in chunk").
 */
public class HarnessTest {
    static int failures = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  PASS " : "  FAIL ") + what); if (!ok) failures++; }

    static class World {
        final ServerLevel level = new ServerLevel();
        final ServerSubLevelContainer container = new ServerSubLevelContainer();
        final SubLevelHoldingChunkMap map = new SubLevelHoldingChunkMap();
        final List<UUID> droppedByVanilla = new ArrayList<>();
        final List<UUID> loadedByVanilla = new ArrayList<>();
        World(Path folder) { map.level = level; map.container = container; container.map = map; map.storage.folder = folder; }

        SubLevelHoldingChunk chunk(int x, int z) { return map.sableguard$getOrLoadHoldingChunk(new ChunkPos(x, z), true); }
        HoldingSubLevel hold(SubLevelHoldingChunk c, UUID id, int bx, int bz, UUID... deps) {
            SubLevelData d = data(id, bx, bz, deps);
            short idx = (short) c.pointers.size();
            c.pointers.add(new SavedSubLevelPointer((short) 0, idx));
            HoldingSubLevel h = new HoldingSubLevel(d, new GlobalSavedSubLevelPointer(c.getChunkPos(), (short) 0, idx));
            c.held.put(id, h); map.all.put(id, h); return h;
        }
        void onDisk(int cx, int cz, UUID id, int bx, int bz, UUID... deps) {
            map.storage.disk.computeIfAbsent(new ChunkPos(cx, cz).toLong(), k -> new LinkedHashMap<>())
                    .put(new SavedSubLevelPointer((short) 0, (short) map.storage.disk.get(new ChunkPos(cx, cz).toLong()).size()), data(id, bx, bz, deps));
        }
        static SubLevelData data(UUID id, int bx, int bz, UUID... deps) {
            SubLevelData d = new SubLevelData(id, new BoundingBox3d(bx, 60, bz, bx + 10, 70, bz + 10), new ArrayList<>(List.of(deps)));
            if (deps.length > 0) { ListTag l = new ListTag(); for (UUID u : deps) l.add(net.minecraft.nbt.NbtUtils.createUUID(u)); d.fullTag().put("loading_dependencies", l); }
            d.fullTag().m.put("display_name", "SL-" + id.toString().substring(0, 4));
            return d;
        }
        void tickAll() { tick(); }
        /** One processChanges(): resolver, vanilla ready-check, restore. */
        void tick() {
            level.time++;
            GuardState s = map.sableguard$getState();
            s.restoreStash();
            OrphanResolver.tick(s);
            for (SubLevelHoldingChunk c : new ArrayList<>(map.loaded.m.values())) {
                if (map.queued.contains(c.getChunkPos())) continue;
                vanillaCollect(c);
            }
            s.restoreStash();
        }
        /** Sable 2.0.6 collectReadySubLevels, minus actual loading (we only record outcomes). */
        void vanillaCollect(SubLevelHoldingChunk c) {
            Set<UUID> visited = new HashSet<>();
            Iterator<Map.Entry<UUID, HoldingSubLevel>> it = c.held.entrySet().iterator();
            List<HoldingSubLevel> alsoLoad = new ArrayList<>();
            check:
            while (it.hasNext()) {
                var e = it.next();
                if (visited.contains(e.getKey())) continue;
                var rel = e.getValue().data().dependencies();
                visited.add(e.getKey()); visited.addAll(rel);
                for (UUID u : rel) {
                    HoldingSubLevel dep = c.held.get(u);
                    if (dep == null) { droppedByVanilla.add(e.getKey()); it.remove(); continue check; }
                    if (!loadable(dep.data())) continue check;
                }
                if (loadable(e.getValue().data())) {
                    loadedByVanilla.add(e.getKey()); it.remove();
                    for (UUID u : rel) { var dep = c.held.get(u); if (dep != null) alsoLoad.add(dep); }
                }
            }
            for (var h : alsoLoad) { c.held.remove(h.data().uuid()); loadedByVanilla.add(h.data().uuid()); }
        }
        boolean loadable(SubLevelData d) { Footprint f = Footprint.of(d.bounds()); return f != null && f.isLoadable(level); }
        void makeTicking(int minCx, int minCz, int maxCx, int maxCz) { for (int x = minCx; x <= maxCx; x++) for (int z = minCz; z <= maxCz; z++) PhysicsChunkTicketManager.TICKING.add(ChunkPos.asLong(x, z)); }
    }

    static UUID u(int n) { return new UUID(0xABCD0000L + n, n); }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("sg");
        Files.createFile(tmp.resolve("r.0.0.slvlr")); // so StorageIndex scans region 0,0 (chunks 0..31)

        System.out.println("T1 #1387: group split across two in-memory holding chunks");
        { PhysicsChunkTicketManager.TICKING.clear(); World w = new World(tmp);
          var c1 = w.chunk(1, 1); var c2 = w.chunk(5, 5);
          w.hold(c1, u(1), 20, 20, u(2)); w.hold(c2, u(2), 90, 90, u(1));
          w.tick();
          check(w.droppedByVanilla.isEmpty(), "nothing dropped (vanilla would drop both)");
          boolean together = (c1.held.containsKey(u(1)) && c1.held.containsKey(u(2))) || (c2.held.containsKey(u(1)) && c2.held.containsKey(u(2)));
          check(together, "both members now in one holding chunk");
          check(w.map.dirty.contains(c1.getChunkPos()) && w.map.dirty.contains(c2.getChunkPos()), "both chunks marked dirty for save");
          check(SableGuardStats.groupsMerged == 1, "merge counted");
          w.makeTicking(0, 0, 10, 10); w.tick(); w.tick();
          check(w.loadedByVanilla.containsAll(List.of(u(1), u(2))), "group loads once its footprint ticks");
        }

        System.out.println("T2: dependency that exists nowhere -> held, then pruned after grace");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(2, 2); var a = w.hold(c, u(10), 40, 40, u(11)); var b = w.hold(c, u(12), 45, 45, u(10), u(11));
          w.makeTicking(0, 0, 10, 10);
          w.tick();
          check(w.droppedByVanilla.isEmpty(), "tick 1: not dropped");
          check(c.held.containsKey(u(10)) && c.held.containsKey(u(12)), "tick 1: restored into holding chunk after the ready-check");
          check(w.loadedByVanilla.isEmpty(), "tick 1: not loaded while waiting");
          for (int i = 0; i < 205; i++) w.tick();
          check(!a.data().dependencies().contains(u(11)) && !b.data().dependencies().contains(u(11)), "missing ref pruned from both members");
          check(b.data().dependencies().equals(List.of(u(10))), "the real dependency is kept");
          check(((ListTag) b.data().fullTag().m.get("loading_dependencies")).size() == 1, "NBT updated too (persisted on save)");
          check(a.data().dependencies().equals(List.of(u(12))), "A normalized to reference B (canonical chain form, as Sable saves it)");
          check(w.droppedByVanilla.isEmpty() && w.loadedByVanilla.containsAll(List.of(u(10), u(12))), "group loads after pruning, nothing dropped");
          check(SableGuardStats.dependenciesPruned == 1, "one prune counted");
        }

        System.out.println("T3: missing part stored in an unloaded holding chunk -> pulled in and merged");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(3, 3); w.hold(c, u(20), 50, 50, u(21));
          w.onDisk(7, 7, u(21), 120, 120, u(20));
          w.tick();
          check(w.map.loaded.containsKey(new ChunkPos(7, 7).toLong()), "foreign holding chunk loaded into Sable's map");
          check(w.droppedByVanilla.isEmpty(), "not dropped meanwhile");
          w.tick();
          var target = w.map.loaded.get(new ChunkPos(3, 3).toLong()).held.containsKey(u(21)) ? c : w.map.loaded.get(new ChunkPos(7, 7).toLong());
          check(target.held.containsKey(u(20)) && target.held.containsKey(u(21)), "re-joined in one chunk");
          check(SableGuardStats.dependenciesPruned == 0, "nothing pruned");
        }

        System.out.println("T4: dependency already loaded in the world -> wait, then prune");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var live = new ServerSubLevel(); live.id = u(31); w.container.live.put(u(31), live);
          var c = w.chunk(4, 4); var a = w.hold(c, u(30), 70, 70, u(31));
          w.makeTicking(0, 0, 10, 10);
          for (int i = 0; i < 50; i++) w.tick();
          check(a.data().dependencies().contains(u(31)) && !w.loadedByVanilla.contains(u(30)), "within grace: still waiting");
          for (int i = 0; i < 60; i++) w.tick();
          check(!a.data().dependencies().contains(u(31)) && w.loadedByVanilla.contains(u(30)), "after grace: pruned and loaded");
          check(w.droppedByVanilla.isEmpty(), "never dropped");
        }

        System.out.println("T5: healthy group is left exactly as vanilla handles it");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(6, 6); w.hold(c, u(40), 100, 100, u(41)); w.hold(c, u(41), 101, 101, u(40));
          w.makeTicking(0, 0, 10, 10);
          w.tick();
          check(w.loadedByVanilla.containsAll(List.of(u(40), u(41))) && w.droppedByVanilla.isEmpty(), "loads on first tick");
          check(w.map.dirty.isEmpty() && SableGuardStats.groupsMerged == 0 && SableGuardStats.storageIndexBuilds == 0, "no merges, no dirty chunks, no index build");
        }

        System.out.println("T6 #1619: group wider than the ticking area -> footprint tickets");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(8, 8);
          SubLevelData wide = new SubLevelData(u(50), new BoundingBox3d(128, 60, 128, 128 + 16 * 6, 70, 140), new ArrayList<>());
          var h = new HoldingSubLevel(wide, new GlobalSavedSubLevelPointer(c.getChunkPos(), (short) 0, (short) 0)); c.held.put(u(50), h);
          w.makeTicking(8, 8, 10, 8); // player near one end only
          for (int i = 0; i < 80; i++) w.tick();
          check(!w.level.cache.tickets.isEmpty(), "tickets added (" + w.level.cache.tickets.size() + ")");
          check(w.level.cache.tickets.stream().noneMatch(p -> PhysicsChunkTicketManager.TICKING.contains(p.toLong())), "only for chunks that were not already ticking");
          for (ChunkPos p : w.level.cache.tickets) PhysicsChunkTicketManager.TICKING.add(p.toLong()); // what the tickets do
          w.tick();
          check(w.loadedByVanilla.contains(u(50)), "group loads once tickets make the footprint tick");
          int before = w.level.cache.tickets.size();
          for (int i = 0; i < 200; i++) w.tick();
          check(w.level.cache.tickets.size() == before, "no re-ticketing while it is loaded and no player is near (tickets expire on their own)");
        }

        System.out.println("T7: oversized/corrupted bounds are never ticketed");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(9, 9);
          SubLevelData huge = new SubLevelData(u(60), new BoundingBox3d(0, 0, 0, 1.0e6, 10, 1.0e6), new ArrayList<>());
          c.held.put(u(60), new HoldingSubLevel(huge, new GlobalSavedSubLevelPointer(c.getChunkPos(), (short) 0, (short) 0)));
          SubLevelData nan = new SubLevelData(u(61), new BoundingBox3d(Double.NaN, 0, 0, 1, 1, 1), new ArrayList<>());
          var c2 = w.chunk(9, 10); c2.held.put(u(61), new HoldingSubLevel(nan, null));
          w.makeTicking(9, 9, 9, 10);
          for (int i = 0; i < 100; i++) w.tick();
          check(w.level.cache.tickets.isEmpty(), "no tickets for 62 500x62 500-chunk or NaN bounds");
          check(SableGuardStats.footprintGroupsTooLarge >= 1, "too-large reported");
        }

        System.out.println("T8: stored copy that never shows up in memory -> capped attempts, then prune");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(10, 10); var a = w.hold(c, u(70), 160, 160, u(71));
          w.onDisk(11, 11, u(71), 170, 170, u(70));
          // the holding chunk loads, but the dependency's data is rejected every time; the chunk is unloaded at each autosave
          w.map.failLoad.add(u(71));
          w.makeTicking(9, 9, 12, 12);
          for (int i = 0; i < 2000; i++) {
              w.tick();
              if (i % 100 == 0) w.map.loaded.m.remove(new ChunkPos(11, 11).toLong());
          }
          check(SableGuardStats.foreignChunksLoaded <= 3, "pulled in at most 3 times (" + SableGuardStats.foreignChunksLoaded + ")");
          check(!a.data().dependencies().contains(u(71)) && w.loadedByVanilla.contains(u(70)), "eventually pruned and loaded instead of looping forever");
          check(w.droppedByVanilla.isEmpty(), "never dropped");
        }

        System.out.println("T9: queued-for-unload chunk is left alone (Sable skips it too)");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(12, 12); var a = w.hold(c, u(80), 200, 200, u(81));
          w.map.queued.add(c.getChunkPos());
          for (int i = 0; i < 300; i++) w.tick();
          check(a.data().dependencies().contains(u(81)) && c.held.containsKey(u(80)), "untouched");
        }

        System.out.println("T2b: removing the last dependency removes the NBT key, like Sable's serializer");
        { SubLevelData d = World.data(u(99), 0, 0, u(98));
          SubLevelEdits.removeDependency(d, u(98));
          check(d.dependencies().isEmpty() && !d.fullTag().contains("loading_dependencies"), "key removed");
        }

        System.out.println("T10: asymmetric dependency lists (A -> [], B -> [A]) are normalized so vanilla does not drop B");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(13, 13); var a = w.hold(c, u(90), 210, 210); var b = w.hold(c, u(91), 212, 212, u(90));
          w.makeTicking(0, 0, 20, 20);
          w.tick();
          check(a.data().dependencies().equals(List.of(u(91))) && b.data().dependencies().equals(List.of(u(90))), "both now reference each other");
          check(w.loadedByVanilla.containsAll(List.of(u(90), u(91))) && w.droppedByVanilla.isEmpty(), "whole group loads, nothing dropped");
        }

        System.out.println("T11: resolver result is stable: a second tick on a healthy world changes nothing");
        { PhysicsChunkTicketManager.TICKING.clear(); SableGuardStats.reset(); World w = new World(tmp);
          var c = w.chunk(14, 14); w.hold(c, u(95), 230, 230, u(96)); w.hold(c, u(96), 231, 231, u(95));
          for (int i = 0; i < 50; i++) w.tick(); // footprint not ticking, no player: just sits in holding
          check(c.held.size() == 2 && w.map.dirty.isEmpty() && w.level.cache.tickets.isEmpty() && SableGuardStats.storageIndexBuilds == 0, "idle group untouched, no index builds, no tickets");
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
        System.out.println("--- log sample ---");
        LogUtils.LINES.stream().limit(14).forEach(l -> System.out.println("  " + (l.length() > 230 ? l.substring(0, 230) + "…" : l)));
        System.exit(failures == 0 ? 0 : 1);
    }
}
