package cn.paperhub.common.storage;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage {
    public static final long PDF_LIMIT = 100L * 1024 * 1024;
    public static final long AVATAR_LIMIT = 2L * 1024 * 1024;
    private final Path root;
    public LocalFileStorage(@Value("${paperhub.storage.root}") String configuredRoot) throws IOException {
        Path configured = Paths.get(configuredRoot).toAbsolutePath().normalize();
        Files.createDirectories(configured);
        root = configured.toRealPath();
        for (String dir : Arrays.asList("papers", "avatars", ".cleanup", ".scratch")) {
            Path child = root.resolve(dir);
            Files.createDirectories(child);
            if (Files.isSymbolicLink(child) || !child.toRealPath().startsWith(root))
                throw new IOException("Storage directory is not owned by storage root");
        }
    }
    public Path root() { return root; }
    public Path resolve(String key) throws IOException {
        if (key == null || !key.matches("(papers/[0-9a-f-]{36}\\.pdf|avatars/[0-9a-f-]{36}\\.png)"))
            throw new FileNotFoundException("Invalid storage key");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || Files.isSymbolicLink(path)
                || !path.getParent().toRealPath().startsWith(root)) throw new FileNotFoundException("Invalid storage path");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !path.toRealPath().startsWith(root))
            throw new FileNotFoundException("Invalid storage path");
        return path;
    }
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }
    public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }

    // Write and force the job before creating/removing any content. A crash leaves a retryable job.
    public String queue(String key) throws IOException {
        resolve(key);
        String name = UUID.randomUUID().toString();
        Path job = root.resolve(".cleanup").resolve(name + ".job");
        try (FileChannel out = FileChannel.open(job, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            out.write(StandardCharsets.UTF_8.encode(key)); out.force(true);
        }
        return name;
    }
    public String queuedKey(String job) throws IOException {
        Path path = jobPath(job);
        if (Files.isSymbolicLink(path) || Files.size(path) > 128) throw new IOException("Invalid cleanup job");
        String key = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        resolve(key);
        return key;
    }
    public Path jobPath(String job) throws IOException {
        if (job == null || !job.matches("[0-9a-f-]{36}")) throw new IOException("Invalid job");
        Path dir = root.resolve(".cleanup");
        if (Files.isSymbolicLink(dir) || !dir.toRealPath().startsWith(root)) throw new IOException("Invalid job directory");
        return dir.resolve(job + ".job");
    }
    public void cancel(String job) throws IOException { Files.deleteIfExists(jobPath(job)); }
    public void discard(Stored file) {
        try { delete(file.key); cancel(file.job); } catch (IOException ignored) { /* durable job will retry */ }
    }
    public Stored pdf(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > PDF_LIMIT) invalid("PDF 为空或超过 100 MiB");
        Stored stored = create("papers/", ".pdf");
        boolean valid = false;
        try {
            MessageDigest digest = sha256();
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(resolve(stored.key), StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    stored.bytes += count;
                    if (stored.bytes > PDF_LIMIT) invalid("PDF 超过 100 MiB");
                    digest.update(buffer, 0, count); output.write(buffer, 0, count);
                }
            }
            byte[] header = new byte[5];
            try (InputStream in = open(stored.key)) {
                if (in.read(header) != 5 || !Arrays.equals(header, "%PDF-".getBytes(StandardCharsets.US_ASCII)))
                    invalid("文件内容不是有效 PDF");
            }
            try (PDDocument pdf = PDDocument.load(resolve(stored.key).toFile(),
                    MemoryUsageSetting.setupMixed(16L * 1024 * 1024, 200L * 1024 * 1024)
                            .setTempDir(root.resolve(".scratch").toFile()))) {
                if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 65535)
                    invalid("PDF 加密、为空或页数超出范围");
                stored.pages = pdf.getNumberOfPages();
            } catch (IOException | IllegalArgumentException ex) { invalid("PDF 无法解析或已加密"); }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            stored.sha256 = hex.toString();
            valid = true;
            return stored;
        } finally { if (!valid) discard(stored); }
    }
    public Stored avatar(byte[] png) throws IOException {
        Stored stored = create("avatars/", ".png");
        boolean saved = false;
        try {
            Files.write(resolve(stored.key), png, StandardOpenOption.CREATE_NEW);
            stored.bytes=png.length; saved=true; return stored;
        } finally { if (!saved) discard(stored); }
    }
    private Stored create(String prefix, String suffix) throws IOException {
        Stored stored = new Stored();
        stored.key=prefix + UUID.randomUUID() + suffix;
        stored.job=queue(stored.key);
        return stored;
    }
    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void invalid(String message) { throw new BusinessException(ErrorCode.INVALID_PARAM, message); }
    public static final class Stored {
        public String key;
        public String job;
        public long bytes;
        public int pages;
        public String sha256;
    }
}

