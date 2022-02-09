package cn.apisium.papershelled.launcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

public class Launcher {

    static final Map<String, String> DEFAULT_CONFIG = new HashMap<>();

    static {
        DEFAULT_CONFIG.put("server.clip", "paperclip.jar");
        DEFAULT_CONFIG.put("server.jar", "server.jar");
        DEFAULT_CONFIG.put("server.lib", "libraries");
        DEFAULT_CONFIG.put("server.java.jvmargs", "");
        DEFAULT_CONFIG.put("server.java.path", "java");
    }

    public static void main(String[] args)
            throws IOException, InterruptedException, ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException, URISyntaxException {
        Properties prop = initConfig();
        if(prop == null){
            return;
        }
        Path server = Paths.get(prop.getProperty("server.jar"));
        Path libraries = Paths.get(prop.getProperty("server.lib"));
        Path clip = Paths.get(prop.getProperty("server.clip"));
        String java = prop.getProperty("server.java.path");

        URL[] classpath = setupClasspath(clip, libraries, server);
        if(Files.exists(server)) {
            Process sp = runServer(classpath, java, prop.getProperty("server.java.jvmargs"), args);
            System.out.println("Server exited with exit code " + sp.waitFor());
        } else {
            System.err.println("Server jar was still not found. Cannot launch.");
        }
    }

    static Properties initConfig()
            throws IOException {
        Properties prop = new Properties();
        Path propPth = Paths.get("papershelled.properties");
        if(!Files.exists(propPth)) {
            Files.createFile(propPth);
            prop.put("server.clip", "paperclip.jar");
            prop.put("server.jar", "server.jar");
            prop.put("server.lib", "libraries");
            prop.put("server.java.jvmargs", "");
            prop.put("server.java.path", "java");
            prop.store(Files.newBufferedWriter(propPth), "Auto generated config. server.clip:Paperclip jar;server.jar:Server jar;server.lib:libraries path;server.jvmargs:arguments used to launch server;server.java.path:java command");
            System.out.println("You are the first time running PaperShelled.");
            System.out.println("Please modify launch arguments in papershelled.properties as your wis.");
            return null;
        }
        prop.load(Files.newBufferedReader(propPth));
        AtomicBoolean cfgRestored = new AtomicBoolean(false);
        DEFAULT_CONFIG.keySet().stream().filter(it -> !prop.containsKey(it)).forEach(it -> {
            System.out.printf("Config entry %s is missing!It will be restored.", it).println();
            prop.put(it, DEFAULT_CONFIG.get(it));
            cfgRestored.set(true);
        });
        if(cfgRestored.get()) {
            prop.store(Files.newBufferedWriter(propPth), "Auto generated config");
            System.out.println("Some config entries were restored.");
            System.out.println("You should have a check on them.");
            return null;
        }
        return prop;
    }

    static URL[] setupClasspath(Path clip, Path libraries, Path server)
            throws IOException, ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        URL[] classpath;
        if(Files.exists(clip)) {
            System.out.println("Paperclip found.Auto completing server files...");
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{clip.toAbsolutePath().toUri().toURL()}, Launcher.class.getClassLoader());
            Class<?> clipClz = loader.loadClass("io.papermc.paperclip.Paperclip");
            Method setupClasspath = clipClz.getDeclaredMethod("setupClasspath");
            setupClasspath.setAccessible(true);
            classpath = (URL[]) setupClasspath.invoke(null);
        } else {
            System.out.println("Paperclip is not present.Using libraries settings...");
            List<URL> l = new LinkedList<>();
            l.add(server.toAbsolutePath().toUri().toURL());
            Files.walkFileTree(libraries, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if(file.getFileName().toString().endsWith(".jar")) {
                        try {
                            l.add(file.toAbsolutePath().toUri().toURL());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            classpath = l.toArray(new URL[0]);
        }
        return classpath;
    }

    static boolean generateServer(Path clip, Path output, String javaPth)
            throws IOException, InterruptedException {
        PaperInfo info = PaperInfo.fromJar(clip);
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(javaPth, "-Dpaperclip.patchonly=true","-jar",clip.toString());
        pb.inheritIO();
        if(pb.start().waitFor() == 0) {
            if(Files.exists(output)) {
                System.err.println("Server jar was already there.Replacing...");
                Files.delete(output);
            }
            Files.copy(info.getOutput(), output);
        } else {
            System.err.println("An error has occurred, the server will be stopped.");
            return false;
        }
        return true;
    }

    static Process runServer(URL[] classpath, String javaPth, String jvmargs, String[] args)
            throws IOException, URISyntaxException {
        ProcessBuilder pb = new ProcessBuilder();
        System.out.println("PaperShelled found server.Attempt to generate launch arguments.");
        StringBuilder sb = new StringBuilder();
        for(URL url: classpath) {
            sb.append(url.getPath().substring(1)).append(';');
        }
        JarFile jf = new JarFile(Paths.get(classpath[0].toURI()).toFile());
        String main = jf.getManifest().getMainAttributes().getValue("Main-Class");
        String url = Launcher.class.getResource("/placeholder").getPath();
        String f = url.substring(0, url.lastIndexOf('!'));
        f = f.substring(f.lastIndexOf("/")+1);
        String[] sja = jvmargs.split(" ");
        String[] params = new String[1 + sja.length + 2 + 1 + 1 + args.length];
        params[0] = javaPth;
        System.arraycopy(sja, 0, params, 1, sja.length);
        params[sja.length + 1] = "-cp";
        params[sja.length + 2] = sb.toString();
        params[sja.length + 3] = "-javaagent:" + f;
        params[sja.length + 4] = main;
        System.arraycopy(args, 0, params, sja.length + 5, args.length);
        System.out.println(String.join(" ", params));
        System.out.println("Launching "+main);
        pb.command(params);
        pb.inheritIO();
        return pb.start();
    }
}
