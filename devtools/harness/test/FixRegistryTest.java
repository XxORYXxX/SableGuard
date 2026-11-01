import dev.sableguard.fix.*;
import java.nio.file.*;

public class FixRegistryTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  PASS " : "  FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("sgcfg");
        Path file = dir.resolve("sableguard/fixes.json");
        System.out.println("F1: first load writes a complete file with defaults");
        FixRegistry.load(dir);
        check(Files.exists(file), "fixes.json written");
        String json = Files.readString(file);
        check(json.contains("\"orphan-resolver\"") && json.contains("\"sublevel-volume-limit\""), "contains core and behaviour fixes");
        boolean behaviourOff = true;
        for (Fix f : FixRegistry.all()) if (f.category() == Fix.Category.BEHAVIOR && f.isEnabledSetting()) behaviourOff = false;
        check(behaviourOff, "every BEHAVIOR fix is off by default");
        check(FixRegistry.all().size() > 40, "registered " + FixRegistry.all().size() + " fixes");

        System.out.println("F2: toggles and options persist across a reload");
        FixRegistry.setEnabled(Fixes.VOLUME_LIMIT, true);
        FixRegistry.setOption(Fixes.VOLUME_LIMIT, "maxBlocks", "1234");
        FixRegistry.setEnabled(Fixes.PANIC_GUARD, false);
        Fixes.VOLUME_LIMIT.toString();
        // simulate a restart by editing state then loading the file again
        FixRegistry.reset(Fixes.VOLUME_LIMIT); // writes defaults
        FixRegistry.setEnabled(Fixes.VOLUME_LIMIT, true);
        FixRegistry.setOption(Fixes.VOLUME_LIMIT, "maxBlocks", "1234");
        String saved = Files.readString(file);
        FixRegistry.setEnabled(Fixes.VOLUME_LIMIT, false); // in-memory change, then overwrite the file with the saved copy
        Files.writeString(file, saved);
        FixRegistry.load(dir);
        check(Fixes.VOLUME_LIMIT.isEnabledSetting(), "enabled state restored");
        check(Fixes.VOLUME_LIMIT.getInt("maxBlocks") == 1234, "int option restored (" + Fixes.VOLUME_LIMIT.getInt("maxBlocks") + ")");
        check(!Fixes.PANIC_GUARD.isEnabledSetting(), "disabled crash guard stays disabled");

        System.out.println("F3: invalid option values are rejected");
        boolean threw = false;
        try { FixRegistry.setOption(Fixes.STAFF_DRAG_CLIP, "yMaxMargin", "NaN"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "NaN rejected for a double option");
        threw = false;
        try { FixRegistry.setOption(Fixes.VOLUME_LIMIT, "maxBlocks", "lots"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "non-number rejected for an int option");
        threw = false;
        try { FixRegistry.setOption(Fixes.VOLUME_LIMIT, "nope", "1"); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "unknown option rejected");
        check(Fixes.VOLUME_LIMIT.getInt("maxBlocks") == 1234, "failed sets leave the value unchanged");

        System.out.println("F4: a corrupt file falls back to defaults and is left untouched");
        Files.writeString(file, "{ this is not json");
        FixRegistry.load(dir);
        check(Files.readString(file).equals("{ this is not json"), "corrupt file not overwritten");

        System.out.println("F5: environment gating");
        FixRegistry.checkEnvironment(id -> id.equals("simulated"));
        FixRegistry.setEnabled(Fixes.VOLUME_LIMIT, true);
        check(Fixes.VOLUME_LIMIT.on(), "simulated fix on when simulated is loaded");
        check(!Fixes.SCGUNS_SHOCK_COIL.on(), "scguns fix idle without scguns");
        FixRegistry.checkEnvironment(id -> false);
        check(!Fixes.VOLUME_LIMIT.on(), "idle once its mod is gone");

        System.out.println(fails == 0 ? "\nALL PASSED" : "\n" + fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
