package io.github.rtx4o9o.discordpdf.convert;

import io.github.rtx4o9o.discordpdf.core.UserFacingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs LibreOffice in headless mode for Office &lt;-&gt; PDF conversion.
 *
 * <p>Each conversion uses its own throw-away LibreOffice profile inside the job workspace, so
 * conversions can run in parallel and nothing (recent-file lists, caches) persists afterwards.
 * {@code OFFICE_COMMAND_PREFIX} lets operators wrap the call in a sandbox, for example
 * {@code "firejail --quiet --net=none"} or {@code "unshare -rn"}, so documents with external
 * links cannot make LibreOffice fetch anything.
 */
public final class LibreOffice {

    private static final List<String> CANDIDATES = List.of(
            "/usr/bin/soffice",
            "/usr/lib/libreoffice/program/soffice",
            "/opt/libreoffice/program/soffice",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
            "C:/Program Files/LibreOffice/program/soffice.exe");

    private final Optional<Path> binary;
    private final Duration timeout;
    private final List<String> prefix;

    public LibreOffice(Optional<Path> configured, Duration timeout, String commandPrefix) {
        this.binary = configured.filter(Files::isExecutable).or(LibreOffice::detect);
        this.timeout = timeout;
        this.prefix = commandPrefix == null || commandPrefix.isBlank()
                ? List.of()
                : Arrays.asList(commandPrefix.trim().split("\\s+"));
    }

    private static Optional<Path> detect() {
        for (String c : CANDIDATES) {
            Path p = Path.of(c);
            if (Files.isExecutable(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public boolean available() {
        return binary.isPresent();
    }

    /**
     * Converts {@code input} to {@code targetFormat} (e.g. "pdf", "doc", "docx").
     * The input file name must carry the right extension so LibreOffice picks the right filter.
     */
    public Path convert(Path input, String targetFormat, Path workDir) throws IOException, InterruptedException {
        Path soffice = binary.orElseThrow(() -> new UserFacingException("error.office_unavailable"));
        Path outDir = Files.createDirectories(workDir.resolve("lo-out"));
        Path profile = Files.createDirectories(workDir.resolve("lo-profile"));

        List<String> cmd = new ArrayList<>(prefix);
        cmd.addAll(List.of(
                soffice.toString(),
                "-env:UserInstallation=" + profile.toUri(),
                "--headless", "--invisible", "--nologo", "--nodefault", "--norestore",
                "--nolockcheck", "--nofirststartwizard",
                "--convert-to", targetFormat,
                "--outdir", outDir.toString(),
                input.toString()));

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("HOME", profile.toString());
        pb.environment().put("TMPDIR", workDir.toString());
        // Defence in depth: point every proxy variable at a closed local port so that, should
        // LibreOffice try to fetch something referenced by a document, the request goes nowhere.
        for (String var : List.of("http_proxy", "https_proxy", "ftp_proxy", "all_proxy",
                "HTTP_PROXY", "HTTPS_PROXY", "FTP_PROXY", "ALL_PROXY")) {
            pb.environment().put(var, "http://127.0.0.1:9");
        }
        pb.environment().remove("no_proxy");
        pb.environment().remove("NO_PROXY");

        Process process = pb.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw new UserFacingException("error.office_timeout", timeout.toSeconds());
        }

        String stem = input.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        Path out = outDir.resolve(stem + "." + targetFormat);
        if (process.exitValue() != 0 || !Files.isRegularFile(out) || Files.size(out) == 0) {
            throw new UserFacingException("error.office_failed");
        }
        return out;
    }
}
