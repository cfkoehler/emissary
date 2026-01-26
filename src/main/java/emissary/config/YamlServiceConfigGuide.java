package emissary.config;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configurator implementation that parses nested YAML and flattens keys using dot-notation. Scalars become single
 * entries, sequences become multi-valued entries, and maps are recursively flattened. Unsupported YAML features
 * (anchors/aliases, custom tags, binary, timestamps) are coerced to strings or rejected.
 */
public class YamlServiceConfigGuide extends ServiceConfigGuide {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(YamlServiceConfigGuide.class);

    /** Default ctor creates an empty guide. */
    public YamlServiceConfigGuide() {
        super();
    }

    /** Parse YAML from InputStream and populate entries. */
    public YamlServiceConfigGuide(InputStream is, String name) throws IOException {
        this();
        try {
            readYamlData(is, name);
        } catch (ConfigSyntaxException e) {
            throw new IOException("Cannot parse YAML configuration file " + e.getMessage(), e);
        }
    }

    private void readYamlData(InputStream is, String name) throws ConfigSyntaxException {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object root = yaml.load(is);
            if (root == null) {
                logger.warn("YAML config {} is empty", name);
                return;
            }
            if (!(root instanceof Map)) {
                throw new ConfigSyntaxException("Top-level YAML must be a mapping: " + name);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) root;
            List<ConfigEntry> flattened = new ArrayList<>();
            flatten("", map, flattened);
            for (ConfigEntry e : flattened) {
                // mimic '=' operator add behavior
                this.serviceParameters.add(e);
                this.values.put(e.getKey(), e.getValue());
            }
        } catch (RuntimeException e) {
            throw new ConfigSyntaxException("Error parsing YAML: " + name + ": " + e.getMessage());
        }
    }

    private static void flatten(String prefix, Map<String, Object> map, List<ConfigEntry> out) throws ConfigSyntaxException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = combine(prefix, entry.getKey());
            Object val = entry.getValue();
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) val;
                flatten(key, child, out);
            } else if (val instanceof Iterable) {
                for (Object item : (Iterable<?>) val) {
                    out.add(new ConfigEntry(key, toStringValue(item)));
                }
            } else {
                out.add(new ConfigEntry(key, toStringValue(val)));
            }
        }
    }

    private static String combine(String prefix, String key) {
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + "." + key;
    }

    @Nullable
    private static String toStringValue(Object o) throws ConfigSyntaxException {
        if (o == null) {
            return null;
        }
        // Disallow complex YAML node types initially
        if (o instanceof byte[]) {
            throw new ConfigSyntaxException("Binary scalars are unsupported in YAML place configs");
        }
        // Coerce booleans, numbers, timestamps, etc. to strings
        return Objects.toString(o, null);
    }
}
