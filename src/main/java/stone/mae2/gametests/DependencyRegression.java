package stone.mae2.gametests;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class DependencyRegression {
    @GameTest(template="empty")
    public static void runtimeMatchesPinnedOfficialAe2AndGtFork(GameTestHelper h) throws Exception {
        try (var stream = DependencyRegression.class.getClassLoader().getResourceAsStream("META-INF/mae2-dependencies.json")) {
            h.assertTrue(stream != null, "Missing embedded dependency lock");
            var lock = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : new String[]{"ae2", "gtceu"}) {
                var spec = lock.getAsJsonObject(id.equals("gtceu") ? "gt" : "ae2");
                var path = ModList.get().getModFileById(id).getFile().getFilePath();
                var digest = MessageDigest.getInstance("SHA-256");
                try (var input = Files.newInputStream(path)) {
                    byte[] chunk = new byte[65536];
                    int n;
                    while ((n = input.read(chunk)) != -1) digest.update(chunk, 0, n);
                }
                h.assertTrue(HexFormat.of().formatHex(digest.digest()).equals(spec.get("sha256").getAsString()), "Runtime artifact differs from pinned " + id);
                if (id.equals("gtceu")) {
                    try (var jar = new JarFile(path.toFile())) {
                        h.assertTrue(spec.get("commit").getAsString().equals(jar.getManifest().getMainAttributes().getValue("Source-Commit")), "GT was not built from specified fork commit");
                    }
                }
            }
        }
        h.succeed();
    }
}
