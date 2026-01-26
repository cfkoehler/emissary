package emissary.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlFlavorMergingTest {

    @Test
    void mergesBaseAndFlavorYaml() throws Exception {
        // Base YAML
        String base = "service:\n  key: BASE\nlist:\n  - A\n";
        // Flavor YAML should override and append
        String flavor = "service:\n  key: FLAVOR\nlist:\n  - B\n";
        InputStream baseStream = new ByteArrayInputStream(base.getBytes());
        Configurator cfg = ConfigUtil.getConfigInfo(baseStream, "emissary.place.SamplePlace.yaml");
        // Simulate flavor via direct merge (ConfigUtil would auto-merge by flavors when files exist)
        InputStream flavorStream = new ByteArrayInputStream(flavor.getBytes());
        Configurator flavorCfg = ConfigUtil.getConfigInfo(flavorStream, "emissary.place.SamplePlace-dev.yaml");
        cfg.merge(flavorCfg);
        assertEquals("FLAVOR", cfg.findStringEntry("service.key"));
        assertEquals(2, cfg.findEntries("list").size());
    }
}

