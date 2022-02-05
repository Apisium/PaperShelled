package cn.apisium.papershelled;

import cn.apisium.papershelled.services.MixinService;
import com.google.common.io.ByteStreams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.*;

public final class PaperShelledAgent {
    private static boolean initialized;
    private static Instrumentation instrumentation;
    private static String obcVersion, obcClassName;
    private static Path serverJar;
    public final static Logger LOGGER = PaperShelledLogger.getLogger(null);
    private final static Remapper remapper = new Remapper() {
        @Override
        public String map(String name) {
            if (!name.startsWith("org/bukkit/craftbukkit/Main") && !name.startsWith("org/bukkit/craftbukkit/libs/") &&
                    name.startsWith("org/bukkit/craftbukkit/") && !name.startsWith("org/bukkit/craftbukkit/v1_")) {
                if (obcVersion == null) throw new IllegalStateException("Cannot detect minecraft version!");
                return obcClassName + name.substring(23);
            }
            return super.map(name);
        }
    };

    @NotNull
    public static Instrumentation getInstrumentation() { return instrumentation; }

    @SuppressWarnings("unused")
    @NotNull
    public static String getReleaseVersion() { return obcVersion; }

    @SuppressWarnings("unused")
    @Nullable
    public static Path getServerJar() { return serverJar; }

    @Nullable
    public static InputStream getResourceAsStream(String name) { return ClassLoader.getSystemResourceAsStream(name); }
    @Nullable
    public static InputStream getClassAsStream(String name) {
        return getResourceAsStream(name.replace('.', '/') + ".class");
    }
    public static byte[] getClassAsByteArray(String name) throws IOException {
        try (InputStream is = getClassAsStream(name)) {
            if (is == null) throw new FileNotFoundException("Class not found: " + name);
            return ByteStreams.toByteArray(is);
        }
    }

    private final static class Transformer implements ClassFileTransformer {
        private boolean hasPaperClip = false;
        private boolean isLegacy = true;
        private boolean errored = false;
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] data) {
            if(!hasPaperClip && className.contains("paperclip")) {
                hasPaperClip = true;
                LOGGER.info("PaperShelled detected Paperclip.");
            }
            if(className.equals("io/papermc/paperclip/Paperclip")) {
                LOGGER.info("Paperclip is being transformed.");
                ClassReader cr = new ClassReader(data);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (name.equals("setupClasspath")) {
                            isLegacy = false;
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                }, ClassReader.SKIP_CODE);
                if (!isLegacy) {
                    LOGGER.info("PaperShelled detected a non-legacy launcher environment.");
                    isLegacy = false;
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                            return name.equals("setupClasspath") ? new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                    if(name.equals("extractAndApplyPatches") && opcode == Opcodes.INVOKESTATIC) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(PaperShelledAgent.class), "delegatePaperclip",
                                                "(Ljava/nio/file/Path;[Ljava/lang/Object;Ljava/nio/file/Path;)Ljava/util/Map;",
                                                false);
                                    } else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                }
                            } : mv;

                        }
                    }, ClassReader.EXPAND_FRAMES);
                    return cw.toByteArray();
                } else LOGGER.info("PaperShelled detected a legacy paperclip environment.");
            } else if(className.equals("io/papermc/paperclip/Main")) {
                return null;
            }
            if(!errored && !hasPaperClip && loader != ClassLoader.getSystemClassLoader() && loader != null) {
                LOGGER.warning("This is not an error but a warning!");
                LOGGER.warning("PaperShelled was not running under AppClassLoader but no Paperclip was found!");
                LOGGER.warning("We don't ensure no problem will happen under current environment!");
                LOGGER.warning("If you encounter any problem, report to us with detailed information.");
                LOGGER.warning(className);
                errored = true;
            }
            if ("org/bukkit/craftbukkit/Main".equals(className)) {
                LOGGER.info(String.format("PaperShelled is running in %s mode %s Paperclip",
                        isLegacy ? "LEGACY" : "NON-LEGACY", hasPaperClip ? "WITH" : "WITHOUT"));
                data = inject(data, "main", "cn/apisium/papershelled/PaperShelledAgent", "init");
                URL url = Objects.requireNonNull(loader.getResource("org/bukkit/craftbukkit/Main.class"));
                try {
                    serverJar = Paths.get(new URI(url.getFile().split("!", 2)[0]));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile()) {
                    Enumeration<JarEntry> itor = jar.entries();
                    while (itor.hasMoreElements()) {
                        String name = itor.nextElement().getName();
                        if (name.startsWith("org/bukkit/craftbukkit/v")) {
                            obcVersion = name.substring(23).split("/", 2)[0];
                            obcClassName = "org/bukkit/craftbukkit/" + obcVersion + "/";
                            LOGGER.info("Detected minecraft version: " + obcVersion);
                            break;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (className.startsWith("org/bukkit/craftbukkit/v1_") && className.endsWith("/CraftServer")) {
                data = inject(data, "loadPlugins", "cn/apisium/papershelled/PaperShelled", "injectPlugins");
                if (obcVersion == null) {
                    obcVersion = className.substring(23).split("/", 2)[0];
                    obcClassName = "org/bukkit/craftbukkit/" + obcVersion + "/";
                    LOGGER.info("Detected minecraft version: " + obcVersion);
                }
            }
            if (!initialized) return data;
            IMixinTransformer transformer = MixinService.getTransformer();
            return transformer == null ? null : transformer.transformClass(MixinEnvironment.getDefaultEnvironment(),
                    className.replace('/', '.'), relocate(data));
        }
    }

    private static void initPaperShelled(Instrumentation instrumentation) {
        Package pkg = PaperShelledAgent.class.getPackage();
        LOGGER.info(pkg.getImplementationTitle() + " version: " + pkg.getImplementationVersion() +
                " (" + pkg.getImplementationVendor() + ")");
        LOGGER.info("You can get the latest updates from: https://github.com/Apisium/PaperShelled");
        System.setProperty("mixin.env.remapRefMap", "true");
        PaperShelledAgent.instrumentation = instrumentation;
        instrumentation.addTransformer(new Transformer(), true);
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
        System.setProperty("papershelled.enable", "true");
    }

    public static void premain(String arg, Instrumentation instrumentation) {
        System.setProperty("mixin.hotSwap", "true");
        initPaperShelled(instrumentation);
        MixinAgent.premain(arg, instrumentation);
    }

    public static void agentmain(String arg, Instrumentation instrumentation) {
        initPaperShelled(instrumentation);
        MixinAgent.agentmain(arg, instrumentation);
    }

    @SuppressWarnings("unused")
    public static void init() throws Throwable {
        initialized = true;
        PaperShelled.init();
        PaperShelledLogger.restore();
    }

    private static byte[] inject(byte[] arr, String method0, String clazz, String method1) {
        ClassReader cr = new ClassReader(arr);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return method0.equals(name) ? new MethodVisitor(api, mv) {
                    @Override
                    public void visitCode() {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazz, method1, "()V", false);
                        super.visitCode();
                    }
                } : mv;
            }
        }, ClassReader.SKIP_DEBUG);
        return cw.toByteArray();
    }

    private static byte[] relocate(byte[] arr) {
        ClassReader cr = new ClassReader(arr);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(Opcodes.ASM9, cw, remapper) { }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }


    public static URL jarLocation() {
        String toProcess = PaperShelled.class.getResource("/placeholder").getPath();
        URL root;
        try {
            root = new URL(toProcess.substring(0, toProcess.indexOf('!')));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    public static Map<String, Map<String, URL>> delegatePaperclip(final Path originalJar, final Object[] patches, final Path repoDir) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            Class<?> arrayType = Array.newInstance(Class.forName("io.papermc.paperclip.PatchEntry"), 0).getClass();
            Method method = Class.forName("io.papermc.paperclip.Paperclip")
                    .getDeclaredMethod("extractAndApplyPatches",  Path.class, arrayType, Path.class);
            method.setAccessible(true);
            Map<String, Map<String, URL>> map = (Map<String, Map<String, URL>>) method.invoke(null, originalJar, patches, repoDir);
            map.get("libraries").put("cn/apisium/papershelled/0.0.1/PaperShelled-0.0.1.jar", jarLocation());
            return map;
        } catch (Throwable e) {
            if(e instanceof Error) {
                throw (Error)e;
            } else if(e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else throw new RuntimeException(e);
        }
    }
}
