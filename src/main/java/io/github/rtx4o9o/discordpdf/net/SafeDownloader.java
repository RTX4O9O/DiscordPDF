package io.github.rtx4o9o.discordpdf.net;

import io.github.rtx4o9o.discordpdf.core.UserFacingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads a user-supplied URL into the job workspace with SSRF protection, a hard size cap,
 * timeouts and manually followed (and re-validated) redirects. No cookies, no credentials,
 * no referrer, nothing about the requesting Discord user is sent.
 */
public final class SafeDownloader {

    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "discordpdf/0.1 (+https://github.com/RTX4O9O/DiscordPDF)";
    private static final Pattern CD_FILENAME_STAR = Pattern.compile("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)");
    private static final Pattern CD_FILENAME = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");

    private final HttpClient client;
    private final Duration timeout;

    public SafeDownloader(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Result of a download: where it was written and the best guess at a display name. */
    public record Downloaded(Path path, String fileName, long size) {
    }

    public static URI parse(String raw) {
        try {
            URI uri = new URI(raw.trim());
            if (!uri.isAbsolute()) {
                throw new UserFacingException("error.url_invalid");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new UserFacingException("error.url_invalid");
        }
    }

    public Downloaded download(URI start, Path target, long maxBytes) throws IOException, InterruptedException {
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            NetworkGuard.checkUri(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpTimeoutException e) {
                throw new UserFacingException("error.download_timeout");
            } catch (IOException e) {
                throw new UserFacingException("error.download_failed");
            }
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                response.body().close();
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new UserFacingException("error.download_failed"));
                try {
                    uri = uri.resolve(new URI(location.trim()));
                } catch (URISyntaxException | IllegalArgumentException e) {
                    throw new UserFacingException("error.url_invalid");
                }
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new UserFacingException("error.download_status", status);
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > maxBytes) {
                response.body().close();
                throw new UserFacingException("error.input_too_large", maxBytes / (1024 * 1024));
            }
            long written = copyLimited(response.body(), target, maxBytes);
            String name = fileNameFrom(response.headers().firstValue("Content-Disposition"), uri);
            return new Downloaded(target, name, written);
        }
        throw new UserFacingException("error.too_many_redirects");
    }

    private static long copyLimited(InputStream body, Path target, long maxBytes) throws IOException {
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = body; OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > maxBytes) {
                    throw new UserFacingException("error.input_too_large", maxBytes / (1024 * 1024));
                }
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    static String fileNameFrom(Optional<String> contentDisposition, URI uri) {
        if (contentDisposition.isPresent()) {
            String cd = contentDisposition.get();
            Matcher star = CD_FILENAME_STAR.matcher(cd);
            if (star.find()) {
                return URLDecoder.decode(star.group(1).trim(), StandardCharsets.UTF_8);
            }
            Matcher plain = CD_FILENAME.matcher(cd);
            if (plain.find()) {
                return plain.group(1).trim();
            }
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !path.endsWith("/")) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (!last.isBlank()) {
                return last;
            }
        }
        return "download";
    }
}
