package cn.apisium.papershelled;

import cn.apisium.papershelled.plugin.PaperShelledPluginLoader;
import org.jetbrains.annotations.NotNull;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public final class PaperShelled {
    private static PaperShelledPluginLoader loader;

    @SuppressWarnings("unused")
    @NotNull
    public static PaperShelledPluginLoader getPluginLoader() { return loader; }

    @SuppressWarnings("ProtectedMemberInFinalClass")
    protected static void init(Instrumentation instrumentation) throws Throwable {
        Path pluginsPath = Paths.get("PaperShelled/plugins");
        Files.createDirectories(pluginsPath);
        loader = new PaperShelledPluginLoader(instrumentation);
        for (Iterator<Path> iter = Files.list(pluginsPath)
                .filter(it -> it.toString().endsWith(".jar") && Files.isRegularFile(it)).iterator(); iter.hasNext(); ) {
            loader.loadPlugin(iter.next().toFile());
        }
    }
}
