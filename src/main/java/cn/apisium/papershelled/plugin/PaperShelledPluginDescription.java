package cn.apisium.papershelled.plugin;

import java.util.Collections;
import java.util.List;

public final class PaperShelledPluginDescription {
    private List<String> mixins;

    public List<String> getMixins() {
        return Collections.unmodifiableList(mixins);
    }
}
