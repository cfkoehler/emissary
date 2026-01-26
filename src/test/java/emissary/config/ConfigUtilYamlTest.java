package emissary.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigUtilYamlTest {
    private Path tempConfigDir;

    @BeforeAll
    void setupAll() throws Exception {
        tempConfigDir = Files.createTempDirectory("emissary-yaml-test");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, tempConfigDir.toString());
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        // re-init ConfigUtil to pick up our config dir
        ConfigUtil.initialize();
    }

    @AfterAll
    void teardownAll() throws Exception {
        System.clearProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
        // delete temp files
        try (Stream<Path> stream = Files.walk(tempConfigDir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Test
    void parsesNestedYamlWithDotFlattening() throws IOException {
        String name = "emissary.test.SamplePlace.yaml";
        String yaml = "SERVICE_KEY: FOO.ID.SamplePlace.http://host:8001/SamplePlace\n" +
                "output:\n  form: MY_FORM\n  paths:\n    - a\n    - b\n";
        Files.writeString(tempConfigDir.resolve(name), yaml);

        Configurator cfg = ConfigUtil.getConfigInfo(name);
        assertEquals("FOO.ID.SamplePlace.http://host:8001/SamplePlace", cfg.findStringEntry("SERVICE_KEY"));
        assertEquals("MY_FORM", cfg.findStringEntry("output.form"));
        List<String> paths = cfg.findEntries("output.paths");
        assertEquals(List.of("a", "b"), paths);
    }

    @Test
    void flavorMergingHonorsYamlOnlyAndOrder() throws Exception {
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "dev,prod");
        ConfigUtil.initialize();
        // base and flavored YAML
        Files.writeString(tempConfigDir.resolve("emissary.test.FlavorPlace.yaml"), "k: base\nlist: [x]\n");
        Files.writeString(tempConfigDir.resolve("emissary.test.FlavorPlace-dev.yaml"), "k: dev\nlist: [y]\n");
        Files.writeString(tempConfigDir.resolve("emissary.test.FlavorPlace-prod.yaml"), "list: [z]\n");

        Configurator cfg = ConfigUtil.getConfigInfo("emissary.test.FlavorPlace.yaml");
        assertEquals("dev", cfg.findStringEntry("k"), "dev flavor should override base");
        // ServiceConfigGuide.merge adds new entries to the top, so list aggregation will be reverse flavor order
        assertEquals(List.of("z", "y", "x"), cfg.findEntries("list"));
    }

    @Test
    void mixedFormatsCauseStartupFailure() throws IOException {
        // Create both .cfg and .yaml for same base
        Files.writeString(tempConfigDir.resolve("emissary.test.MixPlace.cfg"), "a = 1\n");
        Files.writeString(tempConfigDir.resolve("emissary.test.MixPlace.yaml"), "a: 2\n");
        IOException ex = assertThrows(IOException.class, () -> ConfigUtil.getConfigInfo("emissary.test.MixPlace.cfg"));
        assertTrue(ex.getMessage().contains("Mixed config formats"));
    }
}
