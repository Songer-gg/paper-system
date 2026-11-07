package cn.paperhub.common.storage;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
public class FileLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(FileLifecycle.class);
    private final LocalFileStorage storage;
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    public FileLifecycle(LocalFileStorage storage, JdbcTemplate jdbc,
                         org.springframework.transaction.PlatformTransactionManager transactions) {
        this.storage=storage; this.jdbc=jdbc; this.transactions=transactions;
    }

    public void created(LocalFileStorage.Stored file) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    try { storage.cancel(file.job); } catch (IOException ex) { LOG.warn("File job cancellation will retry"); }
                } else storage.discard(file);
            }
        });
    }
    public void deleting(String key) throws IOException {
        requireTransaction();
        // Old demo data may contain legacy paths which must never be traversed or deleted.
        if (key == null || !key.matches("(papers/[0-9a-f-]{36}\\.pdf|avatars/[0-9a-f-]{36}\\.png)")) return;
        String job = storage.queue(key);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) attempt(job);
                else {
                    try { storage.cancel(job); } catch (IOException ex) { LOG.warn("File job cancellation will retry"); }
                }
            }
        });
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("File changes require a database transaction");
    }
    public synchronized void retryPending() {
        try (DirectoryStream<Path> jobs = Files.newDirectoryStream(storage.root().resolve(".cleanup"), "*.job")) {
            int processed = 0;
            for (Path job : jobs) {
                // Mutation transactions have a 60s timeout; uncommitted uploads must never be collected.
                if (Files.getLastModifiedTime(job).toInstant().isBefore(Instant.now().minusSeconds(600))) {
                    if (++processed > 100) break;
                    attempt(job.getFileName().toString().replace(".job", ""));
                }
            }
        } catch (IOException ex) { LOG.warn("Cleanup queue unavailable; retained for a later retry"); }
    }
    private void attempt(String job) {
        try {
            String key = storage.queuedKey(job);
            // Use an independent transaction afterCompletion: the old connection may still be thread-bound.
            TransactionTemplate tx = new TransactionTemplate(transactions);
            tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Boolean referenced = tx.execute(status -> jdbc.queryForObject(
                    "SELECT (SELECT COUNT(*) FROM paper WHERE file_path=? AND deleted_at IS NULL) + " +
                    "(SELECT COUNT(*) FROM `user` WHERE avatar=?)", Long.class, key, key) > 0);
            // A committed upload may have crashed before cancelling its marker. Retain its content.
            if (Boolean.TRUE.equals(referenced)) { storage.cancel(job); return; }
            storage.delete(key); storage.cancel(job);
        } catch (Exception ex) { LOG.warn("File cleanup deferred; job={}", job); }
    }
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name="paperhub.storage.cleanup-enabled", havingValue="true", matchIfMissing=true)
    static class Scheduling {
        private final FileLifecycle lifecycle;
        Scheduling(FileLifecycle lifecycle) { this.lifecycle=lifecycle; }
        @Scheduled(fixedDelay=30000, initialDelay=30000)
        void cleanup() { lifecycle.retryPending(); }
    }
}

