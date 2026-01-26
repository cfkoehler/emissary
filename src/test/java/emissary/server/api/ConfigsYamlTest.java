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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigsYamlTest {

    private Path tempConfigDir;

    @BeforeAll
    void setupAll() throws Exception {
        tempConfigDir = Files.createTempDirectory("emissary-yaml-simple-api-test");
        System.setProperty(ConfigUtil.CONFIG_DIR_PROPERTY, tempConfigDir.toString());
        ConfigUtil.initialize();
    }

    @AfterAll
    void tearDownAll() throws Exception {
        System.clearProperty(ConfigUtil.CONFIG_DIR_PROPERTY);
        try (Stream<Path> s = Files.walk(tempConfigDir)) {
            s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Test
    void getEmissaryConfigHandlesYaml() throws Exception {
        Files.writeString(tempConfigDir.resolve("emissary.place.SamplePlace.yaml"), "foo: bar\n");
        ConfigsResponseEntity resp = Configs.getConfigsResponse("emissary.place.SamplePlace.yaml", false);
        assertNotNull(resp);
    }
}
