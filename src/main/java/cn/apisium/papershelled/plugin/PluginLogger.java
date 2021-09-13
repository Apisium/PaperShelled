package cn.apisium.papershelled.plugin;

import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class PluginLogger extends Logger {
    @NotNull
    public static Logger getLogger(@NotNull PluginDescriptionFile description) {
        Logger logger = new PluginLogger(description);
        if (!LogManager.getLogManager().addLogger(logger)) {
            logger = LogManager.getLogManager().getLogger("PaperShelled:" +
                    (description.getPrefix() != null ? description.getPrefix() : description.getName()));
        }
        return logger;
    }

    private PluginLogger(@NotNull PluginDescriptionFile description) {
        super(description.getPrefix() != null ? description.getPrefix() : description.getName(), null);
    }

    public void setParent(@NotNull Logger parent) {
        if (this.getParent() != null) {
            this.warning("Ignoring attempt to change parent of plugin logger");
        } else {
            this.log(Level.FINE, "Setting plugin logger parent to {0}", parent);
            super.setParent(parent);
        }
    }
}
