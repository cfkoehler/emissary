package emissary.place.sample;

import emissary.core.IBaseDataObject;
import emissary.place.ServiceProviderPlace;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static emissary.core.constants.Configurations.END_FORM;
import static emissary.core.constants.Configurations.NEW_FORM;

/**
 * Example place demonstrating YAML-based configuration. Reads NEW_FORM and END_FORM from a YAML config using
 * dot-notation.
 */
public class YamlExamplePlace extends ServiceProviderPlace {

    private String newForm = "YAML_EXAMPLE";
    private String endForm = "FINI";

    // Remote constructor
    public YamlExamplePlace(String cfgInfo, String dir, String placeLoc) throws IOException {
        super(cfgInfo, dir, placeLoc);
        configurePlace();
    }

    // Stream constructor
    public YamlExamplePlace(InputStream cfgInfo) throws IOException {
        super(cfgInfo, "YamlExamplePlace.localhost:8001");
        configurePlace();
    }

    // Local/test constructor
    public YamlExamplePlace(String cfgInfo) throws IOException {
        super(cfgInfo, "YamlExamplePlace.localhost:8001");
        configurePlace();
    }

    private void configurePlace() {
        newForm = configG.findStringEntry(NEW_FORM, newForm);
        endForm = configG.findStringEntry(END_FORM, endForm);
    }

    @Override
    public List<IBaseDataObject> processHeavyDuty(IBaseDataObject d) {
        // A trivial transform: just set a form to show the config was read
        if (!d.transformHistory().isEmpty()) {
            d.setCurrentForm(newForm);
        } else {
            d.setCurrentForm(endForm);
        }
        return Collections.emptyList();
    }
}

