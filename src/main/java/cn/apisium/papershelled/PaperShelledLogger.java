package cn.apisium.papershelled;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.logging.*;

public class PaperShelledLogger extends Logger {
    public static ConsoleHandler LOGGER_HANDLER = new ConsoleHandler();
    private static ArrayList<PaperShelledLogger> instances = new ArrayList<>();

    static {
        LOGGER_HANDLER.setFormatter(new Formatter() {
            @SuppressWarnings("deprecation")
            @Override
            public String format(LogRecord record) {
                Date date = new Date(record.getMillis());
                return String.format("[%02d:%02d:%02d %s]: [%s] %s%n", date.getHours(), date.getMinutes(),
                        date.getSeconds(), record.getLevel().getName(), record.getLoggerName(), record.getMessage());
            }
        });
    }

    {
        if (instances != null) {
            setUseParentHandlers(false);
            addHandler(LOGGER_HANDLER);
            instances.add(this);
        }
    }

    @NotNull
    public static Logger getLogger(@Nullable String name) {
        if (name == null) name = "";
        name = "PaperShelled" + (name.isEmpty() ? "" : ":" + name);
        Logger logger = new PaperShelledLogger(name);
        if (!LogManager.getLogManager().addLogger(logger)) {
            logger = LogManager.getLogManager().getLogger(name);
        }
        return logger;
    }

    private PaperShelledLogger(@Nullable String name) {
        super(name, null);
    }

    public void setParent(@NotNull Logger parent) {
        if (this.getParent() != null) {
            this.warning("Ignoring attempt to change parent of plugin logger");
        } else {
            this.log(Level.FINE, "Setting plugin logger parent to {0}", parent);
            super.setParent(parent);
        }
    }

    protected static void restore() {
        instances.forEach(it -> {
            it.removeHandler(LOGGER_HANDLER);
            it.setUseParentHandlers(true);
        });
        instances = null;
    }
}
