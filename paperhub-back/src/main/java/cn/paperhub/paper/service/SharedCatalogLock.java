package cn.paperhub.paper.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import javax.sql.DataSource;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

/** One MySQL-server lock serializes shared identities and duplicate evidence across app instances. */
@Component
public class SharedCatalogLock {
    public static final String NAME="paperhub:shared-catalog";
    private static final Logger LOG=LoggerFactory.getLogger(SharedCatalogLock.class);
    private final DataSource dataSource;
    public SharedCatalogLock(DataSource dataSource) { this.dataSource=dataSource; }

    public void acquire() {
        if(!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Shared catalog writes require a write transaction");
        for(TransactionSynchronization synchronization:TransactionSynchronizationManager.getSynchronizations())
            if(synchronization instanceof Release && ((Release)synchronization).source==this) return;
        Connection connection=DataSourceUtils.getConnection(dataSource);
        boolean acquired=false,registered=false;
        try {
            try(PreparedStatement statement=connection.prepareStatement("SELECT GET_LOCK(?,5)")) {
                statement.setString(1,NAME); DataSourceUtils.applyTransactionTimeout(statement,dataSource);
                try(ResultSet result=statement.executeQuery()) {
                    if(!result.next()) throw new BusinessException(ErrorCode.SERVER_ERROR);
                    int value=result.getInt(1);
                    if(result.wasNull()) throw new BusinessException(ErrorCode.SERVER_ERROR);
                    if(value!=1) throw new BusinessException(ErrorCode.CONFLICT,"共享资源正在处理，请稍后重试");
                    acquired=true;
                }
            }
            TransactionSynchronizationManager.registerSynchronization(new Release(this,connection));
            registered=true;
        } catch(SQLException ex) {
            // The server might have acquired the lock before the response failed; do not reuse this session.
            invalidate(connection);
            if(ex.getErrorCode()==1213 || ex.getErrorCode()==1205 || "40001".equals(ex.getSQLState()))
                throw new BusinessException(ErrorCode.CONFLICT);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
        finally {
            if(acquired && !registered) release(connection);
            DataSourceUtils.releaseConnection(connection,dataSource);
        }
    }
    private void release(Connection connection) {
        try(PreparedStatement statement=connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1,NAME); statement.setQueryTimeout(5);
            try(ResultSet result=statement.executeQuery()) {
                if(!result.next() || result.getInt(1)!=1) throw new SQLException("Lock ownership lost");
            }
        } catch(SQLException ex) {
            // A pooled connection must never be returned while potentially holding a session lock.
            LOG.error("Shared catalog lock release failed; invalidating its connection");
            invalidate(connection);
        }
    }
    private void invalidate(Connection connection) {
        if(dataSource instanceof HikariDataSource) {
            try { ((HikariDataSource)dataSource).evictConnection(connection); return; }
            catch(Exception ex) { LOG.error("Pool eviction failed; aborting the shared catalog connection"); }
        }
        try { connection.abort(Runnable::run); }
        catch(Exception ex) { LOG.error("Failed to abort shared catalog connection"); }
    }
    private static final class Release implements TransactionSynchronization {
        private final SharedCatalogLock source;
        private final Connection connection;
        private Release(SharedCatalogLock source,Connection connection) { this.source=source; this.connection=connection; }
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public void afterCompletion(int status) { source.release(connection); }
    }
}

