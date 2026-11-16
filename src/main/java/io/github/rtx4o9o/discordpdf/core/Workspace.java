package io.github.rtx4o9o.discordpdf.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A private, per-job scratch directory. Every file a job touches lives here and the whole
 * directory is deleted when the job ends, whether it succeeded or failed.
 */
public final class Workspace implements AutoCloseable {

    private static final String PREFIX = "job-";

    private final Path dir;
    private final AtomicInteger counter = new AtomicInteger();

    private Workspace(Path dir) {
        this.dir = dir;
    }

    public static Workspace create(Path baseDir) throws IOException {
        Files.createDirectories(baseDir);
        Path dir;
        try {
            dir = Files.createTempDirectory(baseDir, PREFIX,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) {
            dir = Files.createTempDirectory(baseDir, PREFIX); // non-POSIX file systems (Windows)
        }
        return new Workspace(dir);
    }

    public Path dir() {
        return dir;
    }

    /** A fresh, not-yet-existing file path with the given suffix (e.g. ".pdf"). */
    public Path newFile(String suffix) {
        return dir.resolve("f" + counter.incrementAndGet() + suffix);
    }

    /** A fresh, empty sub-directory. */
    public Path newDir(String hint) throws IOException {
        return Files.createDirectory(dir.resolve(hint + "-" + counter.incrementAndGet()));
    }

    @Override
    public void close() {
        deleteRecursively(dir);
    }

    /**
     * Points the JVM temp directory (used internally by ImageIO, POI and others) at a
     * directory under the work dir that is wiped at start-up and shutdown, and turns off
     * ImageIO's disk cache, so no library leaves fragments of user files in /tmp.
     */
    public static void redirectJvmTemp(Path baseDir) throws IOException {
        javax.imageio.ImageIO.setUseCache(false);
        Path tmp = baseDir.resolve(PREFIX + "jvm-tmp");
        Files.createDirectories(tmp);
        System.setProperty("java.io.tmpdir", tmp.toAbsolutePath().toString());
    }

    /** Removes job directories left behind by a crash or a hard kill. Called at start-up. */
    public static void purgeStale(Path baseDir) {
        if (!Files.isDirectory(baseDir)) {
            return;
        }
        try (Stream<Path> children = Files.list(baseDir)) {
            children.filter(p -> p.getFileName().toString().startsWith(PREFIX))
                    .forEach(Workspace::deleteRecursively);
        } catch (IOException ignored) {
            // best effort
        }
    }

    static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best effort; the start-up purge will catch anything left over
        }
    }
}
