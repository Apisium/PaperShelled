package cn.apisium.papershelled.launcher;

import cn.apisium.papershelled.PaperShelledAgent;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.jar.JarFile;

@SuppressWarnings("unused")
public class Launcher {
    public static void launch(String[] args) {
        try {
            Class<?> clipClz = Launcher.class.getClassLoader().loadClass("io.papermc.paperclip.Paperclip");
            Method setupClasspath = clipClz.getDeclaredMethod("setupClasspath");
            setupClasspath.setAccessible(true);
            Arrays.stream((URL[]) setupClasspath.invoke(null)).map(u -> {
                try {
                    return new JarFile(Paths.get(u.toURI()).toFile());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).forEach(PaperShelledAgent.getInstrumentation()::appendToSystemClassLoaderSearch);
            Thread server = new Thread(() -> {
                try {
                    Method findMainClass = clipClz.getDeclaredMethod("findMainClass");
                    findMainClass.setAccessible(true);
                    String name = (String) findMainClass.invoke(null);
                    Class<?> main = Class.forName(name);
                    System.out.print("Launching ");
                    System.out.println(name);
                    main.getMethod("main", String[].class).invoke(null, (Object)args);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "Server Thread");
            server.setContextClassLoader(Launcher.class.getClassLoader());
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
