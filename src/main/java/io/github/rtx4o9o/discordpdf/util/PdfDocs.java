package io.github.rtx4o9o.discordpdf.util;

import io.github.rtx4o9o.discordpdf.core.UserFacingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loading and privacy clean-up for PDF documents. */
public final class PdfDocs {

    private PdfDocs() {
    }

    /**
     * Loads a PDF and enforces the page limit. Password-protected files are rejected;
     * owner-password-only files (print/copy restrictions) are opened and saved unencrypted.
     */
    public static PDDocument load(Path file, int maxPages) {
        PDDocument doc;
        try {
            doc = Loader.loadPDF(file.toFile());
        } catch (InvalidPasswordException e) {
            throw new UserFacingException("error.pdf_encrypted");
        } catch (IOException e) {
            throw new UserFacingException("error.pdf_corrupt");
        }
        if (doc.getNumberOfPages() == 0) {
            closeQuietly(doc);
            throw new UserFacingException("error.pdf_empty");
        }
        if (doc.getNumberOfPages() > maxPages) {
            int n = doc.getNumberOfPages();
            closeQuietly(doc);
            throw new UserFacingException("error.too_many_pages", n, maxPages);
        }
        if (doc.isEncrypted()) {
            doc.setAllSecurityToBeRemoved(true);
        }
        return doc;
    }

    /**
     * Removes document-level metadata that commonly carries personal data:
     * the Info dictionary (author, creator tool, dates), XMP packets on the document
     * and on each page, page thumbnails and application private data (PieceInfo).
     */
    public static void scrubMetadata(PDDocument doc) {
        doc.setDocumentInformation(new PDDocumentInformation());
        doc.getDocumentCatalog().setMetadata(null);
        doc.getDocumentCatalog().getCOSObject().removeItem(COSName.getPDFName("PieceInfo"));
        for (PDPage page : doc.getPages()) {
            page.setMetadata(null);
            page.getCOSObject().removeItem(COSName.getPDFName("Thumb"));
            page.getCOSObject().removeItem(COSName.getPDFName("PieceInfo"));
        }
    }

    /** Scrubs metadata and saves. */
    public static void saveClean(PDDocument doc, Path target) throws IOException {
        scrubMetadata(doc);
        doc.save(target.toFile());
    }

    /**
     * Rewrites a PDF produced by another program without its metadata (LibreOffice copies
     * author, company and similar fields from the source document). Returns the page count.
     */
    public static int scrubFile(Path pdf) throws IOException {
        Path tmp = pdf.resolveSibling(pdf.getFileName() + ".clean");
        int pages;
        try (PDDocument doc = load(pdf, Integer.MAX_VALUE)) {
            pages = doc.getNumberOfPages();
            saveClean(doc, tmp);
        }
        Files.move(tmp, pdf, StandardCopyOption.REPLACE_EXISTING);
        return pages;
    }

    public static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
            // nothing useful to do
        }
    }
}
