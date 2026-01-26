package emissary.server.api;

import emissary.client.response.ConfigsResponseEntity;
import emissary.config.ConfigUtil;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigsYamlApiTest {
    private Path tempConfigDir;

    @BeforeAll
    void setupAll() throws Exception {
        tempConfigDir = Files.createTempDirectory("emissary-yaml-api-test");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, tempConfigDir.toString());
        System.setProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY, "dev");
        ConfigUtil.initialize();
    }

    @AfterAll
    void teardownAll() throws Exception {
        System.clearProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        System.clearProperty(ConfigUtil.CONFIG_FLAVOR_PROPERTY);
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
    void detailedApiIncludesBaseFlavorAndCombinedForYaml() throws IOException {
        Files.writeString(tempConfigDir.resolve("emissary.test.ApiPlace.yaml"), "a: base\nlist:\n  - b\n");
        Files.writeString(tempConfigDir.resolve("emissary.test.ApiPlace-dev.yaml"), "a: dev\nlist:\n  - c\n");

        ConfigsResponseEntity detailed = Configs.getEmissaryConfigDetailed("emissary.test.ApiPlace.yaml");
        assertEquals(3, detailed.getLocal().getConfigs().size(), "base, flavor, combined");
        // base entries
        assertTrue(detailed.getLocal().getConfigs().get(0).getConfigs().contains("emissary.test.ApiPlace.yaml"));
        // flavor entries
        assertTrue(detailed.getLocal().getConfigs().get(1).getConfigs().contains("emissary.test.ApiPlace-dev.yaml"));
        // combined entries
        List<String> combinedList = detailed.getLocal().getConfigs().get(2).getConfigs();
        assertTrue(combinedList.contains("emissary.test.ApiPlace.yaml"));
        assertTrue(combinedList.contains("emissary.test.ApiPlace-dev.yaml"));
        // combined value present
        assertTrue(detailed.getLocal().getConfigs().get(2).getEntries().stream()
                .anyMatch(e -> e.getKey().equals("a") && "dev".equals(e.getValue())));
    }

    @Test
    void simpleApiReturnsCombinedYaml() throws IOException {
        Files.writeString(tempConfigDir.resolve("emissary.test.SimpleApiPlace.yaml"), "x: 1\n");
        ConfigsResponseEntity resp = Configs.getEmissaryConfig("emissary.test.SimpleApiPlace.yaml");
        assertEquals(1, resp.getLocal().getConfigs().size());
        assertEquals("1", resp.getLocal().getConfigs().get(0).getEntries().get(0).getValue());
    }
}
