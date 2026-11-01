import dev.sableguard.fix.*;
import dev.sableguard.tools.ScalableLuxOverride;
import java.nio.file.*;
public class LuxTest {
    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("sglux");
        Files.writeString(dir.resolve("fml.toml"), "# FML config\nearlyWindowControl = true\nmaxThreads = -1\n");
        FixRegistry.load(dir);
        FixRegistry.checkEnvironment(id -> true);
        FixRegistry.setEnabled(Fixes.SCALABLELUX_BYPASS, true);
        ScalableLuxOverride.applyIfEnabled(dir);
        com.mojang.logging.LogUtils.LINES.forEach(System.out::println);
        System.out.println("--- after enable\n" + Files.readString(dir.resolve("fml.toml")));
        ScalableLuxOverride.applyIfEnabled(dir); // idempotent
        FixRegistry.setEnabled(Fixes.SCALABLELUX_BYPASS, false);
        ScalableLuxOverride.applyIfEnabled(dir);
        System.out.println("--- after disable\n" + Files.readString(dir.resolve("fml.toml")));
        // a hand-written override must survive disabling
        Files.writeString(dir.resolve("fml.toml"), "maxThreads = -1\n[dependencyOverrides]\nsable = [\"-scalablelux\"]\n");
        ScalableLuxOverride.applyIfEnabled(dir);
        System.out.println("--- hand-written kept\n" + Files.readString(dir.resolve("fml.toml")));
    }
}
