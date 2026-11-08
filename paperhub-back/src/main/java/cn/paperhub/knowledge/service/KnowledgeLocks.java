package cn.paperhub.knowledge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KnowledgeLocks {
    private final JdbcTemplate jdbc;
    public KnowledgeLocks(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /**
     * Classification assignments take shared dictionary locks before any paper row.
     * Category mutations take the category tree FOR UPDATE; tag mutations lock their tag first.
     * All locks are owned/released by the database transaction, including rollback.
     */
    public void beforePaperWrite() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Dictionary locks require a transaction");
        jdbc.queryForList("SELECT id FROM category ORDER BY id FOR SHARE", Long.class);
        jdbc.queryForList("SELECT id FROM tag ORDER BY id FOR SHARE", Long.class);
    }
}

