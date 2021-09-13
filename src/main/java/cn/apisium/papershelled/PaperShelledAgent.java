package cn.apisium.papershelled;

import cn.apisium.papershelled.services.MixinService;
import org.objectweb.asm.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.tools.agent.MixinAgent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Date;
import java.util.logging.*;

@SuppressWarnings("deprecation")
public final class PaperShelledAgent {
    private static boolean initialized;
    private static Instrumentation instrumentation;
    public final static Logger LOGGER = Logger.getLogger("PaperShelled");

    static {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                Date date = new Date(record.getMillis());
                return String.format("[%02d:%02d:%02d %s]: [%s] %s%n", date.getHours(), date.getMinutes(),
                        date.getSeconds(), record.getLevel().getName(), record.getLoggerName(), record.getMessage());
            }
        });
        LOGGER.addHandler(handler);
    }

    private final static class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if ("org/bukkit/craftbukkit/Main".equals(className)) {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassVisitor(Opcodes.ASM8, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return "main".equals(name) ? new MethodVisitor(api, mv) {
                            @Override
                            public void visitCode() {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "cn/apisium/papershelled/PaperShelledAgent",
                                        "init", "()V", false);
                                super.visitCode();
                            }
                        } : mv;
                    }
                }, ClassReader.SKIP_DEBUG);
                return cw.toByteArray();
            } else if (className.startsWith("org/bukkit/craftbukkit/") && className.endsWith("/CraftServer")) {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassVisitor(Opcodes.ASM8, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        return "loadPlugins".equals(name) ? new MethodVisitor(api, mv) {
                            @Override
                            public void visitCode() {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "cn/apisium/papershelled/PaperShelled",
                                        "injectPlugins", "()V", false);
                                super.visitCode();
                            }
                        } : mv;
                    }
                }, ClassReader.SKIP_DEBUG);
                classfileBuffer = cw.toByteArray();
                if (!initialized) return classfileBuffer;
            }
            if (!initialized) return null;
            IMixinTransformer transformer = MixinService.getTransformer();
            return transformer == null ? null : transformer.transformClass(MixinEnvironment.getDefaultEnvironment(),
                    className.replace('/', '.'), classfileBuffer);
        }
    }

    private static void initPaperShelled(Instrumentation instrumentation) {
        PaperShelledAgent.instrumentation = instrumentation;
        instrumentation.addTransformer(new Transformer());
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
        PaperShelled.init(instrumentation);
    }
}
