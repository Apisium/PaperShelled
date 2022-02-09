package cn.apisium.papershelled.launcher;

import java.io.IOException;
import java.nio.file.*;

public class PaperInfo {

    private final String version;
    private final String output;
    private final String digest;

    public PaperInfo(String digest, String version, String output) {
        this.version = version;
        this.output = output;
        this.digest = digest;
    }

    public static PaperInfo fromJar(Path p) throws IOException {
        FileSystem fs = FileSystems.newFileSystem(p, Launcher.class.getClassLoader());
        Path vlist = fs.getPath("META-INF", "versions.list");
        String[] parts = Files.readAllLines(vlist).get(0).split("\t");
        return new PaperInfo(parts[0], parts[1], parts[2]);
    }

    public Path getOutput() {
        return Paths.get("versions/"+output).toAbsolutePath();
    }

    public String getVersion() {
        return version;
    }

    public String getDigest() {
        return digest;
    }
}
