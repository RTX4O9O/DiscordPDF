package me.rtx4090.discordpdf.core;

import me.rtx4090.discordpdf.net.SafeDownloader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where an input comes from. Fetching always copies it into the job workspace. */
public sealed interface InputSource {

    InputFile fetch(Workspace ws, SafeDownloader downloader, long maxBytes) throws IOException, InterruptedException;

    /**
     * A file on the web: a Discord attachment (CDN URL, name and size known up front)
     * or a link the user pasted (name taken from the response).
     */
    record Remote(URI uri, String nameHint, long knownSize) implements InputSource {

        public static Remote link(String rawUrl) {
            return new Remote(SafeDownloader.parse(rawUrl), null, -1);
        }

        @Override
        public InputFile fetch(Workspace ws, SafeDownloader downloader, long maxBytes)
                throws IOException, InterruptedException {
            if (knownSize > maxBytes) {
                throw new UserFacingException("error.input_too_large", maxBytes / (1024 * 1024));
            }
            Path dest = ws.newFile(".in");
            SafeDownloader.Downloaded d = downloader.download(uri, dest, maxBytes);
            String name = nameHint != null ? nameHint : d.fileName();
            return new InputFile(dest, name, FileTypes.sniff(dest, name), d.size());
        }
    }

    /** A local file (offline CLI only). */
    record Local(Path path) implements InputSource {

        @Override
        public InputFile fetch(Workspace ws, SafeDownloader downloader, long maxBytes) throws IOException {
            long size = Files.size(path);
            if (size > maxBytes) {
                throw new UserFacingException("error.input_too_large", maxBytes / (1024 * 1024));
            }
            Path dest = ws.newFile(".in");
            Files.copy(path, dest);
            String name = path.getFileName().toString();
            return new InputFile(dest, name, FileTypes.sniff(dest, name), size);
        }
    }
}
