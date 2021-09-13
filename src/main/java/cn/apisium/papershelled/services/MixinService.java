package cn.apisium.papershelled.services;

import cn.apisium.papershelled.MixinPlatformAgent;
import cn.apisium.papershelled.PaperShelled;
import com.google.common.io.ByteStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.transformers.MixinClassReader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class MixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
    private final ClassLoader loader = getClass().getClassLoader();
    private final static ByteArrayInputStream refMap =
            new ByteArrayInputStream("{\"mappings\":{}}".getBytes(StandardCharsets.UTF_8));
    private static IMixinTransformer transformer;

    public static IMixinTransformer getTransformer() { return transformer; }

    @Override
    public String getName() {
        return "PaperShelled";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList(MixinPlatformAgent.class.getName());
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(getName());
    }
    @Override
    public InputStream getResourceAsStream(String name) {
        if (name.equals("mixin.refmap.json")) return refMap;
        try {
            String[] names = name.split("\\|", 2);
            if (names.length == 2) {
                return Objects.requireNonNull(PaperShelled.getPluginLoader().getPlugin(names[0]),
                                "No such plugin: " + names[1]).getResource(names[1]);
            }
            URI uri = URI.create(name);
            try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                return new ByteArrayInputStream(Files.readAllBytes(fs.provider().getPath(uri)));
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory && transformer == null)
            transformer = ((IMixinTransformerFactory) internal).createTransformer();
        super.offer(internal);
    }

    @Override
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name);
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name);
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException {
        return this.getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException {
        if (!runTransformers) {
            throw new IllegalArgumentException("ModLauncher service does not currently support retrieval of untransformed bytecode");
        }

        String canonicalName = name.replace('/', '.');

        try (InputStream is = loader.getResourceAsStream(canonicalName + ".class")) {
            if (is != null) {
                byte[] classBytes = ByteStreams.toByteArray(is);
                if (classBytes.length != 0) {
                    ClassNode classNode = new ClassNode();
                    ClassReader classReader = new MixinClassReader(classBytes, canonicalName);
                    classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                    return classNode;
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        throw new ClassNotFoundException(canonicalName);
    }
}
