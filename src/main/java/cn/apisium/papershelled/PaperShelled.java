package cn.apisium.papershelled;

import cn.apisium.papershelled.plugin.PaperShelledPluginLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class PaperShelled {
    private static JsonObject versionJson;
    private static PaperShelledPluginLoader loader;

    @SuppressWarnings("unused")
    @Nullable
    public static JsonObject getVersionJson() { return versionJson; }

    @SuppressWarnings("unused")
    @NotNull
    public static PaperShelledPluginLoader getPluginLoader() { return loader; }

    @SuppressWarnings("ProtectedMemberInFinalClass")
    protected static void init() throws Throwable {
        try (InputStream is = PaperShelledAgent.getResourceAsStream("version.json")) {
            if (is != null) versionJson = new JsonParser().parse(new InputStreamReader(is)).getAsJsonObject();
        }
        Path pluginsPath = Paths.get("PaperShelled/plugins");
        Files.createDirectories(pluginsPath);
        loader = new PaperShelledPluginLoader();

        loadPlugins(pluginsPath).forEach(it -> {
            try {
                PaperShelledAgent.LOGGER.info("Loading " + it.getDescription().getFullName());
                it.onLoad();
            } catch (Throwable ex) {
                ex.printStackTrace();
                PaperShelledAgent.LOGGER.log(Level.SEVERE, ex.getMessage() + " initializing " +
                        ex.getMessage() + " (Is it up to date?)", ex);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static List<Plugin> loadPlugins(Path pluginsPath) throws Throwable {
        List<Plugin> result = new ArrayList<>();
        HashMap<String, File> plugins = new HashMap<>();
        HashSet<String> loadedPlugins = new HashSet<>();
        HashMap<String, String> pluginsProvided = new HashMap<>();
        HashMap<String, Collection<String>> dependencies = new HashMap<>();
        HashMap<String, Collection<String>> softDependencies = new HashMap<>();

        for (Iterator<Path> iter = Files.list(pluginsPath)
                .filter(it -> it.toString().endsWith(".jar") && Files.isRegularFile(it)).iterator(); iter.hasNext(); ) {
            File file = iter.next().toFile();
            PluginDescriptionFile description;
            try {
                description = loader.getPluginDescription(file);
                String name = description.getName();
                if (name.equalsIgnoreCase("bukkit") || name.equalsIgnoreCase("minecraft") || name.equalsIgnoreCase("mojang")) {
                    PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "': Restricted Name");
                    continue;
                } else if (description.getRawName().indexOf(' ') != -1) {
                    PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "': uses the space-character (0x20) in its name");
                    continue;
                }
            } catch (InvalidDescriptionException ex) {
                ex.printStackTrace();
                PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "'", ex);
                continue;
            }

            File replacedFile = plugins.put(description.getName(), file);
            if (replacedFile != null) {
                PaperShelledAgent.LOGGER.severe(String.format("Ambiguous plugin name `%s' for files `%s' and `%s'",
                        description.getName(), file.getPath(), replacedFile.getPath()));
            }

            String removedProvided = pluginsProvided.remove(description.getName());
            if (removedProvided != null) {
                PaperShelledAgent.LOGGER.warning(String.format("Ambiguous plugin name `%s'. It is also provided by `%s'",
                        description.getName(), removedProvided));
            }

            for (String provided : description.getProvides()) {
                File pluginFile = plugins.get(provided);
                if (pluginFile != null) {
                    PaperShelledAgent.LOGGER.warning(String.format("`%s provides `%s' while this is also the name of `%s'",
                            file.getPath(), provided, pluginFile.getPath()));
                } else {
                    String replacedPlugin = pluginsProvided.put(provided, description.getName());
                    if (replacedPlugin != null) {
                        PaperShelledAgent.LOGGER.warning(String.format("`%s' is provided by both `%s' and `%s'",
                                provided, description.getName(), replacedPlugin));
                    }
                }
            }

            Collection<String> softDependencySet = description.getSoftDepend();
            if (!softDependencySet.isEmpty()) {
                if (softDependencies.containsKey(description.getName())) {
                    // Duplicates do not matter, they will be removed together if applicable
                    softDependencies.get(description.getName()).addAll(softDependencySet);
                } else {
                    softDependencies.put(description.getName(), new LinkedList<>(softDependencySet));
                }
            }

            Collection<String> dependencySet = description.getDepend();
            if (!dependencySet.isEmpty()) {
                dependencies.put(description.getName(), new LinkedList<>(dependencySet));
            }

            Collection<String> loadBeforeSet = description.getLoadBefore();
            if (!loadBeforeSet.isEmpty()) {
                for (String loadBeforeTarget : loadBeforeSet) {
                    if (softDependencies.containsKey(loadBeforeTarget)) {
                        softDependencies.get(loadBeforeTarget).add(description.getName());
                    } else {
                        Collection<String> shortSoftDependency = new LinkedList<>();
                        shortSoftDependency.add(description.getName());
                        softDependencies.put(loadBeforeTarget, shortSoftDependency);
                    }
                }
            }
        }

        while (!plugins.isEmpty()) {
            boolean missingDependency = true;
            Iterator<Map.Entry<String, File>> pluginIterator = plugins.entrySet().iterator();

            while (pluginIterator.hasNext()) {
                Map.Entry<String, File> entry = pluginIterator.next();
                String plugin = entry.getKey();

                if (dependencies.containsKey(plugin)) {
                    Iterator<String> dependencyIterator = dependencies.get(plugin).iterator();

                    while (dependencyIterator.hasNext()) {
                        String dependency = dependencyIterator.next();

                        // Dependency loaded
                        if (loadedPlugins.contains(dependency)) {
                            dependencyIterator.remove();

                            // We have a dependency not found
                        } else if (!plugins.containsKey(dependency) && !pluginsProvided.containsKey(dependency)) {
                            missingDependency = false;
                            pluginIterator.remove();
                            softDependencies.remove(plugin);
                            dependencies.remove(plugin);

                            PaperShelledAgent.LOGGER.log(
                                    Level.SEVERE,
                                    "Could not load '" + entry.getValue().getPath() + "'",
                                    new UnknownDependencyException("Unknown dependency " + dependency +
                                            ". Please download and install " + dependency + " to run this plugin."));
                            break;
                        }
                    }

                    if (dependencies.containsKey(plugin) && dependencies.get(plugin).isEmpty()) {
                        dependencies.remove(plugin);
                    }
                }
                if (softDependencies.containsKey(plugin)) {

                    // Soft depend is no longer around
                    softDependencies.get(plugin).removeIf(softDependency -> !plugins.containsKey(softDependency) &&
                            !pluginsProvided.containsKey(softDependency));

                    if (softDependencies.get(plugin).isEmpty()) {
                        softDependencies.remove(plugin);
                    }
                }
                if (!(dependencies.containsKey(plugin) || softDependencies.containsKey(plugin)) && plugins.containsKey(plugin)) {
                    // We're clear to load, no more soft or hard dependencies left
                    File file = plugins.get(plugin);
                    pluginIterator.remove();
                    missingDependency = false;

                    try {
                        Plugin loadedPlugin = loader.loadPlugin(file);
                        result.add(loadedPlugin);
                        loadedPlugins.add(loadedPlugin.getName());
                        loadedPlugins.addAll(loadedPlugin.getDescription().getProvides());
                    } catch (InvalidPluginException ex) {
                        ex.printStackTrace();
                        PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "'", ex);
                    }
                }
            }

            if (missingDependency) {
                pluginIterator = plugins.entrySet().iterator();

                while (pluginIterator.hasNext()) {
                    Map.Entry<String, File> entry = pluginIterator.next();
                    String plugin = entry.getKey();

                    if (!dependencies.containsKey(plugin)) {
                        softDependencies.remove(plugin);
                        missingDependency = false;
                        File file = entry.getValue();
                        pluginIterator.remove();

                        try {
                            Plugin loadedPlugin = loader.loadPlugin(file);
                            result.add(loadedPlugin);
                            loadedPlugins.add(loadedPlugin.getName());
                            loadedPlugins.addAll(loadedPlugin.getDescription().getProvides());
                            break;
                        } catch (InvalidPluginException ex) {
                            ex.printStackTrace();
                            PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "'", ex);
                        }
                    }
                }
                // We have no plugins left without a depend
                if (missingDependency) {
                    softDependencies.clear();
                    dependencies.clear();
                    Iterator<File> failedPluginIterator = plugins.values().iterator();

                    while (failedPluginIterator.hasNext()) {
                        File file = failedPluginIterator.next();
                        failedPluginIterator.remove();
                        PaperShelledAgent.LOGGER.log(Level.SEVERE, "Could not load '" + file.getPath() + "': circular dependency detected");
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings({ "unused", "unchecked", "SynchronizationOnLocalVariableOrMethodParameter" })
    public static void injectPlugins() throws Throwable {
        PluginManager pm0 = Bukkit.getPluginManager();
        if (!(pm0 instanceof SimplePluginManager)) return;
        SimplePluginManager pm = (SimplePluginManager) pm0;
        Field field = SimplePluginManager.class.getDeclaredField("plugins");
        field.setAccessible(true);
        ((List<Plugin>) field.get(pm)).addAll(loader.getPlugins());

        field = SimplePluginManager.class.getDeclaredField("lookupNames");
        field.setAccessible(true);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) field.get(pm);
        loader.getPlugins().forEach(it -> {
            lookupNames.put(it.getDescription().getName(), it);
            for (String provided : it.getDescription().getProvides()) lookupNames.putIfAbsent(provided, it);
        });

        field = SimplePluginManager.class.getDeclaredField("fileAssociations");
        field.setAccessible(true);
        Map<Pattern, PluginLoader> fileAssociations = (Map<Pattern, PluginLoader>) field.get(pm);
        synchronized (pm) {
            for (Pattern pattern : loader.getPluginFileFilters()) fileAssociations.put(pattern, loader);
        }
    }
}
