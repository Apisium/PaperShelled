package cn.apisium.papershelled.launcher;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

public class Launcher {

    static final Map<String, String> DEFAULT_CONFIG = new HashMap<>();

    static {
        DEFAULT_CONFIG.put("server.jar", "");
        DEFAULT_CONFIG.put("server.java.jvmargs", "");
        DEFAULT_CONFIG.put("server.java.path", "");
        DEFAULT_CONFIG.put("server.java.args", "");
    }

    public static void main(String[] args)
            throws IOException, InterruptedException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException, URISyntaxException {
        Properties prop = initConfig();
        if(prop == null){
            return;
        }
        Path server = Paths.get(prop.getProperty("server.jar"));
        String java = prop.getProperty("server.java.path");

        URL[] classpath = setupClasspath(server);
        if(Files.exists(server)) {
            String arg = prop.getProperty("server.java.args", "");
            String[] argarr;
            if(!arg.isEmpty()) {
                argarr = arg.split(" ");
            } else argarr = new String[0];
            Process sp = runServer(classpath, java, prop.getProperty("server.java.jvmargs"), argarr);
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
            prop.putAll(DEFAULT_CONFIG);
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

    static URL[] setupClasspath(Path server)
            throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        URL[] classpath;
        if(Files.exists(server)) {
            System.out.println("Server jar found.Auto completing server files...");
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{server.toAbsolutePath().toUri().toURL()}, Launcher.class.getClassLoader());
            try {
                Class<?> clipClz = loader.loadClass("io.papermc.paperclip.Paperclip");
                System.out.println("It's paperclip!Constructing server...");
                Method setupClasspath = clipClz.getDeclaredMethod("setupClasspath");
                setupClasspath.setAccessible(true);
                classpath = (URL[]) setupClasspath.invoke(null);
            } catch (ClassNotFoundException e) {
                System.out.println("It's a regular server jar.");
                classpath = new URL[]{server.toAbsolutePath().toUri().toURL()};
            }
        } else {
            System.err.println("The server jar does not exists.Will not launch the server.");
            classpath = null;
        }
        return classpath;
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
        String url = Launcher.class.getResource("/cn/apisium/papershelled/launcher/Launcher.class").getPath();
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
        System.out.println("Launching "+main);
        pb.command(params);
        pb.inheritIO();
        return pb.start();
    }
}
