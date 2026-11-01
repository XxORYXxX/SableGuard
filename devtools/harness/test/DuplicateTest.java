import dev.sableguard.DuplicateMods;
public class DuplicateTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  PASS " : "  FAIL ") + what); if (!ok) fails++; }
    public static void main(String[] a) {
        System.out.println("D1: mods from the user's pack");
        check(DuplicateMods.HOLDING_CHUNK_FIXER.equals(DuplicateMods.canonical("sablesublevelholdingchunkfix", "Sable Holding Chunk Fixer", "sablesublevelholdingchunkfix-1.5.3.jar")), "Holding Chunk Fixer");
        check(DuplicateMods.SABLE_FIX_MOD.equals(DuplicateMods.canonical("sable_fix_mod", "Sable Fix Mod", "sable_fix_mod-1.1.0.jar")), "Sable Fix Mod");
        check(DuplicateMods.CHUNK_GUARD.equals(DuplicateMods.canonical("whatever_id", "Something", "sable-chunk-guard-1.0.0.jar")), "Chunk Guard by jar name (unknown id)");
        check(DuplicateMods.CHUNK_GUARD.equals(DuplicateMods.canonical("x", "Sable Chunk Guard", "x.jar")), "Chunk Guard by display name");
        check(DuplicateMods.CBC_RANGEFINDER_FIX.equals(DuplicateMods.canonical("cbc_sable_fix", "CBC Sable Rangefinder Fix", "cbc-sable-fix-1.0.3.jar")), "CBC Sable Rangefinder Fix");
        System.out.println("D2: mods that must never be touched");
        for (String[] m : new String[][]{{"sable", "Sable", "sable-neoforge-1.21.1-2.0.6.jar"}, {"cbc_ballistics", "CBC Ballistics", "cbc_ballistics-1.3.0a-neoforge-1.21.1.jar"},
{"sableexplosionfix", "Sable Explosion Fix", "sableexplosionfix-1.0.0.jar"},
                {"chunky", "Chunky", "Chunky-NeoForge-1.4.23.jar"}, {"sablephysicscompat", "Sable Physics Compat", "sablephysicscompat-1.3.0.jar"},
                {"sableguard", "SableGuard", "sableguard-neoforge-1.21.1-0.3.0.jar"}, {"smoothchunk", "Smooth Chunk", "smoothchunk-1.21-4.4.jar"}}) {
            check(DuplicateMods.canonical(m[0], m[1], m[2]) == null, m[2] + " left alone");
        }
        System.out.println(fails == 0 ? "\nALL PASSED" : "\n" + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
