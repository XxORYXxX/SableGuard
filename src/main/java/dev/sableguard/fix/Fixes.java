package dev.sableguard.fix;

import java.util.Set;

import static dev.sableguard.fix.Fix.Category.BEHAVIOR;
import static dev.sableguard.fix.Fix.Category.COMPAT;
import static dev.sableguard.fix.Fix.Category.CRASH_GUARD;
import static dev.sableguard.fix.Fix.Category.STORAGE;
import static dev.sableguard.fix.Fix.Category.TOOL;

/**
 * Every fix SableGuard ships, in one place. Mixins reference these constants directly.
 * <p>
 * Origins: "SableGuard" is original work. Fixes from SableCat / Create-Aeronautics:Compatibility + Bug fixes
 * (MIT, © FuckSable Contributors) and Sable Fix Mod (MIT, © finkkk) were reviewed against Sable 2.0.6 and rewritten
 * where their original form was unsafe; see the README for what changed. "Chunk Guard (reimplemented)" is an
 * independent implementation of the idea described by Sable Chunk Guard (ARR); none of its code was used.
 */
public final class Fixes {
    private static final String SG = "SableGuard";
    private static final String CAT = "SableCat / CACB (MIT)";
    private static final String CAT_REWORKED = "SableCat / CACB (MIT), reworked by SableGuard";
    private static final String SFM = "Sable Fix Mod (MIT)";
    private static final String SFM_REWORKED = "Sable Fix Mod (MIT), reworked by SableGuard";
    private static final String CHUNK_GUARD_ORIGIN = "Sable Chunk Guard idea, independent reimplementation";

    private static final String HCF_ORIGIN = "Sable Holding Chunk Fixer idea (All Rights Reserved), independent reimplementation";
    private static final String CBC_ORIGIN = "CBC Sable Rangefinder Fix idea (MIT), reimplemented against CBC Ballistics 1.3.0a";

    private static final Set<String> NONE = Set.of();

    // ---- SableGuard core -------------------------------------------------------------------------------------
    public static final Fix ORPHAN_RESOLVER = FixRegistry.register("orphan-resolver", STORAGE, true, NONE, SG,
                    "Keeps held sub-level groups with a missing dependency from being dropped (the 'invisible, stuck in storage' bug): re-joins split groups, pulls in the holding chunk that has the missing part, and only as a last resort removes the reference.")
            .option("missingDependencyGraceTicks", 200)
            .option("liveDependencyGraceTicks", 100)
            .option("foreignChunkLoadCooldownTicks", 200);

    public static final Fix FOOTPRINT_TICKETS = FixRegistry.register("footprint-tickets", STORAGE, true, NONE, SG,
                    "Groups wider than the block-ticking area never load in Sable; adds short-lived chunk tickets over their footprint while a player is near.")
            .option("waitTicks", 60)
            .option("maxChunks", 256);

    public static final Fix VELOCITY_SANITIZE = FixRegistry.register("velocity-sanitize", CRASH_GUARD, true, NONE, SG,
                    "Clamps extreme saved velocities and zeroes non-finite ones when a sub-level loads.")
            .option("maxLinear", 512.0)
            .option("maxAngular", 50.0);

    public static final Fix HEATMAP_GUARD = FixRegistry.register("heatmap-guard", CRASH_GUARD, true, NONE, SG,
            "Skips block-split processing of sub-levels that are already removed (prevents the 'assembly attempted inside plot of already removed sub-level' crash).");

    public static final Fix SPLIT_PARENT_GUARD = FixRegistry.register("split-parent-guard", CRASH_GUARD, true, NONE, SG,
            "Stops a block split mid-way when moving one island out left the parent sub-level with no valid mass and Sable removed it, instead of crashing on the next island ('assembly attempted inside plot of already removed sub-level').");

    public static final Fix SAVE_GUARDS = FixRegistry.register("save-guards", STORAGE, true, NONE, SG,
            "Save hardening: iterate a snapshot of holding chunks, survive a null holding chunk, never load a second copy of a sub-level, forget held copies of deleted sub-levels.");

    // ---- Storage (from SableCat, reviewed) -----------------------------------------------------------------
    public static final Fix CHAIN_SAVE_GUARD = FixRegistry.register("chain-save-guard", STORAGE, true, NONE, CAT,
            "One sub-level failing to save no longer aborts the save of every sub-level after it in the same autosave.");

    public static final Fix STORAGE_LOG_THROTTLE = FixRegistry.register("storage-log-throttle", TOOL, true, NONE, CAT_REWORKED,
                    "Logs a failing storage pointer once per minute instead of every tick, and skips re-reading it during that minute. Unlike the original, the pointer is never removed.")
            .option("cooldownSeconds", 60);

    public static final Fix PARTIAL_HEADER_WRITE = FixRegistry.register("partial-header-write", STORAGE, true, NONE, CAT,
            "Writes only the 4-byte header slot that changed instead of the whole 4 KB header, so a crash mid-write cannot damage the other 1023 slots.");

    public static final Fix RESCUE_CAPTURE = FixRegistry.register("rescue-capture", TOOL, true, NONE, CAT,
                    "Keeps sub-levels whose load failed in memory so /sableguard rescue can inspect and retry them.")
            .option("rescueY", 200.0);

    public static final Fix MANIFEST = FixRegistry.register("manifest", TOOL, true, NONE, CAT,
            "Records every sub-level seen (name, dimension, last position) in <world>/sableguard/manifest.json, so lost ones can be located.");

    public static final Fix SCHEDULED_BACKUP = FixRegistry.register("scheduled-backup", TOOL, true, NONE, CAT_REWORKED,
                    "Periodically archives each dimension's sublevels folder to <world>/sableguard_backups/scheduled. Copies right after a save, then zips the copy off-thread.")
            .option("intervalHours", 24.0)
            .option("keep", 14);

    // ---- Crash guards (from SableCat, reviewed) ------------------------------------------------------------
    public static final Fix PANIC_GUARD = FixRegistry.register("panic-guard", CRASH_GUARD, true, NONE, CAT_REWORKED,
            "Skips physics calls on sub-levels that were already removed instead of letting the Rust engine panic. Unlike the original, other physics bodies (boxes) are never blocked.");

    public static final Fix CONSTRAINT_SELF_FIX = FixRegistry.register("constraint-self-fix", CRASH_GUARD, true, NONE, CAT,
            "Ignores constraints that link a sub-level to itself instead of crashing.");

    public static final Fix PHYSICS_TICKET_GUARD = FixRegistry.register("physics-ticket-guard", CRASH_GUARD, true, NONE, CAT,
            "Skips one tick of Sable's chunk-ticket update when vanilla's DistanceManager state is corrupted, instead of crashing.");

    public static final Fix PLOT_HOLDER_GUARD = FixRegistry.register("plot-holder-guard", CRASH_GUARD, true, NONE, CAT_REWORKED,
            "Skips block updates in a sub-level plot whose chunk holder no longer exists instead of crashing.");

    public static final Fix UDP_PACKET_GUARD = FixRegistry.register("udp-invalid-packet-guard", CRASH_GUARD, true, NONE, CAT,
            "Drops Sable UDP packets with an unknown packet id instead of spamming errors.");

    public static final Fix ENTITY_GETTER_GUARD = FixRegistry.register("sublevel-entity-guard", CRASH_GUARD, true, NONE, CAT,
                    "Refuses entity searches over absurdly large boxes near sub-levels, which can hang the server.")
            .option("maxBoxSize", 10000.0);

    public static final Fix ENTITY_LOOKUP_REMOVE_GUARD = FixRegistry.register("entity-lookup-remove-guard", CRASH_GUARD, true, NONE, CAT,
            "If vanilla's entity id map is corrupted, rebuilds it and retries instead of crashing the tick.");

    public static final Fix LIGHT_BOUNDS_GUARD = FixRegistry.register("light-engine-bounds-guard", CRASH_GUARD, true, NONE, CAT,
            "Ignores lighting updates for sections outside the world height instead of crashing.");

    public static final Fix BLOCK_COORDINATE_GUARD = FixRegistry.register("block-coordinate-guard", CRASH_GUARD, true, NONE, CAT,
                    "Refuses to set or destroy blocks at overflowed coordinates (usually a modded item bug).")
            .option("xzLimit", 30_000_000)
            .option("yMin", -2048)
            .option("yMax", 4096);

    public static final Fix CHUNK_GUARD = FixRegistry.register("chunk-guard", CRASH_GUARD, true, NONE, CHUNK_GUARD_ORIGIN,
            "Sable physics never loads or generates a chunk synchronously; missing terrain is skipped for that tick and picked up once it has loaded in the background. Prevents 0 TPS freezes when sub-levels move into new terrain.");

    // ---- Duplication (from Sable Fix Mod, reviewed) ---------------------------------------------------------
    public static final Fix ASSEMBLY_DROP_GUARD = FixRegistry.register("assembly-drop-guard", STORAGE, true, NONE, SFM_REWORKED,
            "While Sable moves blocks (assemble/disassemble), suppresses item drops at the moved positions, so inventories and crops are not duplicated. Unlike the original, inventories are never emptied.");

    public static final Fix ASSEMBLY_SHAPE_SKIP = FixRegistry.register("assembly-shape-skip", STORAGE, true, NONE, SFM,
            "Skips redundant shape updates after Sable copies block states, so rails, dust, torches and buttons are not duplicated.");

    public static final Fix ASSEMBLY_INVENTORY_CLEAR = FixRegistry.register("assembly-inventory-clear", BEHAVIOR, false, NONE, SFM,
            "Original Sable Fix Mod approach: empties every item capability of moved block entities. RISK: multiblock and networked storage (Create vaults, drawer controllers) can expose inventories that are not their own and lose items.");

    // ---- Compatibility (from SableCat, reviewed) ------------------------------------------------------------
    public static final Fix COPYCATS_LIFT = FixRegistry.register("copycats-lift-compat", COMPAT, true, Set.of("copycats"), CAT,
            "Prevents a crash when a Copycats+ block without a facing property is placed on a sub-level.");

    public static final Fix TYPEWRITER_SERVER_FIX = FixRegistry.register("typewriter-server-fix", COMPAT, true, Set.of("simulated"), CAT,
            "Dedicated server: a client-only class referenced by the Linked Typewriter no longer crashes the server (already fixed in Simulated 1.3.2; kept as a guard).");

    public static final Fix STEAM_VENT_SERVER_FIX = FixRegistry.register("aeronautics-server-fix", COMPAT, true, Set.of("aeronautics"), CAT_REWORKED,
            "Dedicated server: the Steam Vent value box never touches client-only classes. Rewritten without @Overwrite.");

    public static final Fix CARRYON_COMPAT = FixRegistry.register("carryon-compat", COMPAT, true, Set.of("carryon"), CAT,
            "CarryOn placing an entity or player on a sub-level puts it at the real world position instead of the plot coordinates.");

    public static final Fix CTT_CONCURRENT = FixRegistry.register("ctt-concurrent-fix", COMPAT, true, Set.of("createthreadedtrains", "create"), CAT,
            "Thread-safety fixes between Create ThreadedTrains and Create carriages.");

    public static final Fix CTT_LOG_SPAM = FixRegistry.register("ctt-log-spam-fix", TOOL, true, Set.of("createthreadedtrains"), CAT,
            "Logs each Create ThreadedTrains error type once.");

    public static final Fix CTT_TIMEOUT = FixRegistry.register("ctt-posttick-timeout-guard", BEHAVIOR, false, Set.of("createthreadedtrains"), CAT_REWORKED,
                    "Stops waiting for a stuck Create ThreadedTrains worker after a timeout instead of freezing the server. Off by default: the cancelled worker may leave that tick's train state half-updated.")
            .option("timeoutSeconds", 30);

    public static final Fix TRACK_GRAPH_NULL = FixRegistry.register("create-trackgraph-null-guard", CRASH_GUARD, true, Set.of("create"), CAT,
            "Create train navigation no longer crashes on a null track node.");

    public static final Fix TRAIN_DETACH_NULL_EDGE = FixRegistry.register("create-train-detach-nulledge-guard", CRASH_GUARD, true, Set.of("create"), CAT,
            "Create train detachment skips points with a null track edge instead of crashing.");

    public static final Fix EFFORTLESS_PARTICLES = FixRegistry.register("effortless-particle-fix", COMPAT, true, Set.of("effortless"), CAT,
            "Client: no block-break particles for unloaded chunks (Effortless Building + Sable crash).");

    public static final Fix AIRWARS_PLOT_CHUNKS = FixRegistry.register("airwars-plot-chunks", COMPAT, true, Set.of("create_the_air_wars"), SG,
            "Client: Create: The Air Wars no longer captures Sable plot chunks for its remote cameras (assembled sub-levels were invisible and could not be edited).");

    public static final Fix SABLEDYNLIGHTS_THREAD_GUARD = FixRegistry.register("sabledynlights-thread-guard", COMPAT, true, Set.of("sabledynlights"), SG,
            "Client: Sable Dynamic Lights only adds its virtual light on the render thread. In singleplayer the integrated server's light engines read its client-only maps while the client changes them (ConcurrentModificationException while ticking the world).");

    public static final Fix SCALABLELUX_COMPAT = FixRegistry.register("scalablelux-compat", COMPAT, true, Set.of("scalablelux"), CAT,
            "Correct sub-level lighting when ScalableLux is installed. Needs scalablelux-incompat-bypass.");

    public static final Fix SCALABLELUX_BYPASS = FixRegistry.register("scalablelux-incompat-bypass", BEHAVIOR, false, NONE, CAT,
            "Writes a dependency override to config/fml.toml so Sable can start with ScalableLux. Takes effect after a restart.");

    public static final Fix SCGUNS_SHOCK_COIL = FixRegistry.register("shock-coil-coordinate-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns shock coils on sub-levels use world coordinates (prevents hangs and crashes).");
    public static final Fix SCGUNS_TURRET_SYNC = FixRegistry.register("turret-visual-sync-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns turret barrels rotate visibly on sub-levels.");
    public static final Fix SCGUNS_TURRET_MUZZLE = FixRegistry.register("turret-muzzle-position-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns turrets fire from their real world muzzle position on sub-levels.");
    public static final Fix SCGUNS_TURRET_MULTI_TICK = FixRegistry.register("turret-multi-tick-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns turrets tick once per game tick on sub-levels.");
    public static final Fix SCGUNS_TURRET_TARGETING = FixRegistry.register("turret-targeting-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns turret aiming works on sub-levels.");
    public static final Fix SCGUNS_ENEMY_TARGETING = FixRegistry.register("enemy-turret-targeting-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns enemy turret aiming works on sub-levels.");
    public static final Fix SCGUNS_ENEMY_MUZZLE = FixRegistry.register("enemy-turret-muzzle-fix", COMPAT, true, Set.of("scguns"), CAT,
            "Scorched Guns enemy turrets fire from their real world muzzle position on sub-levels.");

    public static final Fix CBC_RANGEFINDER = FixRegistry.register("cbc-rangefinder-sublevel-fix", COMPAT, true, Set.of("cbc_ballistics"), CBC_ORIGIN,
            "CBC Ballistics: when its virtual projectile (rangefinder and ballistic calculator) hits a sub-level, the result is the real world point instead of plot coordinates millions of blocks away, which froze the calculator and sent absurd targets over the radio.");

    public static final Fix CBC_PARTICLE_CAP = FixRegistry.register("cbc-particle-cap", CRASH_GUARD, true, Set.of("cbc_ballistics"), CBC_ORIGIN,
                    "Caps CBC Ballistics guide particles (maxPerLine, maxPerCircle) so absurd coordinates cannot freeze the client.")
            .option("maxPerLine", 200)
            .option("maxPerCircle", 100);

    // ---- Tools -----------------------------------------------------------------------------------------------
    public static final Fix DISABLE_DUPLICATE_MODS = FixRegistry.register("disable-duplicate-mods", TOOL, true, NONE, SG,
            "Renames the jars of mods SableGuard fully replaces (Sable Fix Mod, Sable Chunk Guard, Sable Holding Chunk Fixer, CBC Sable Rangefinder Fix, SableCat, FuckSable) to .jar.disabled. Never deletes anything. Takes effect on the next start; their duplicate fixes are skipped in the meantime.");

    // ---- Behaviour changes: always off by default ------------------------------------------------------------
    public static final Fix VOLUME_LIMIT = FixRegistry.register("sublevel-volume-limit", BEHAVIOR, false, Set.of("simulated"), CAT_REWORKED,
                    "Refuses to assemble structures larger than maxBlocks with the Aeronautics assembler. Unlike the original, Sable's own splitting is never affected (that path crashes on a refused assembly).")
            .option("maxBlocks", 8192);

    public static final Fix COMMAND_BLOCK_BAN = FixRegistry.register("command-block-sublevel-fix", BEHAVIOR, false, NONE, CAT,
            "Prevents placing command blocks on sub-levels.");

    public static final Fix PLAYER_POSITION_GUARD = FixRegistry.register("player-position-guard", BEHAVIOR, false, NONE, CAT,
                    "Pulls players back inside the world border and below build height + yMaxMargin.")
            .option("yMaxMargin", 1000.0);

    public static final Fix STAFF_DRAG_CLIP = FixRegistry.register("physics-staff-drag-clipfix", BEHAVIOR, false, Set.of("simulated"), CAT,
                    "Clamps sub-levels inside the world border and build height + yMaxMargin.")
            .option("yMaxMargin", 1000.0);

    public static final Fix SLIME_BEARING = FixRegistry.register("aeronautics-slime-bearfix", BEHAVIOR, false, Set.of("simulated"), CAT,
            "Slime blocks do not stick to swivel bearings during assembly.");

    public static final Fix FROGPORT_LIMIT = FixRegistry.register("frogport-extract-limit", BEHAVIOR, false, Set.of("create"), CAT,
                    "Frogports skip pulling from inventories with more than maxSlots slots. WARNING: this includes AE2 interfaces and large storage.")
            .option("maxSlots", 256);

    public static final Fix KEEP_NEAR_PLAYERS = FixRegistry.register("keep-sublevels-near-players", BEHAVIOR, false, NONE, HCF_ORIGIN,
                    "Sub-levels are not unloaded while any chunk they cover is inside a player's view distance, even if part of them leaves the loaded area. Turned on automatically when Sable Holding Chunk Fixer is replaced. Groups wider than maxChunks are left to Sable.")
            .option("maxChunks", 256);

    public static final Fix DEATH_GRACE = FixRegistry.register("death-grace", BEHAVIOR, false, NONE, HCF_ORIGIN,
                    "For graceSeconds after a player dies, sub-levels around where they died stay loaded as if they were still there. Turned on automatically when Sable Holding Chunk Fixer is replaced.")
            .option("graceSeconds", 120);

    private Fixes() {
    }

    /** Forces class initialisation, which registers every fix. */
    static void init() {
    }
}
