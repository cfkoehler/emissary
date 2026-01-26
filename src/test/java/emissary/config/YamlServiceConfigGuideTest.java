package emissary.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlServiceConfigGuideTest {

    @Test
    void parsesNestedYamlWithDotNotationAndLists() throws Exception {
        String yaml = "" +
                "SERVICE_KEY: CFGTEST.ID.CfgTestPlace.http://host:9999/CfgTestPlace\n" +
                "output:\n" +
                "  form: MY_FORM\n" +
                "allowedForms:\n" +
                "  - A\n" +
                "  - B\n" +
                "  - C\n" +
                "feature:\n" +
                "  enabled: true\n";
        YamlServiceConfigGuide g = new YamlServiceConfigGuide(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
        // scalar
        assertEquals("CFGTEST.ID.CfgTestPlace.http://host:9999/CfgTestPlace", g.findStringEntry("SERVICE_KEY"));
        // nested scalar -> dot notation
        assertEquals("MY_FORM", g.findStringEntry("output.form"));
        // list becomes multi-valued entries
        List<String> forms = g.findEntries("allowedForms");
        assertEquals(List.of("A", "B", "C"), forms);
        // boolean coerced to string
        assertEquals("true", g.findStringEntry("feature.enabled"));
    }

    @Test
    void rejectsNonMappingTopLevel() {
        String yaml = "- a\n- b\n";
        Exception ex = assertThrows(IOException.class,
                () -> new YamlServiceConfigGuide(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "list.yaml"));
        assertEquals(true, ex.getMessage().contains("Top-level YAML must be a mapping"));
    }
}
