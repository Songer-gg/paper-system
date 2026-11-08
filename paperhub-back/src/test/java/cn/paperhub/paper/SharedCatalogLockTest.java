package cn.paperhub.paper;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.service.SharedCatalogLock;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SharedCatalogLockTest {
    DataSource dataSource;
    Connection connection;
    PreparedStatement acquire,release;
    ResultSet acquired,released;
    SharedCatalogLock lock;
    @BeforeEach void transaction() throws Exception {
        dataSource=mock(DataSource.class); connection=mock(Connection.class);
        acquire=mock(PreparedStatement.class); release=mock(PreparedStatement.class);
        acquired=mock(ResultSet.class); released=mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?,5)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquired); when(release.executeQuery()).thenReturn(released);
        when(acquired.next()).thenReturn(true); when(acquired.getInt(1)).thenReturn(1);
        when(released.next()).thenReturn(true); when(released.getInt(1)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.bindResource(dataSource,new ConnectionHolder(connection));
        lock=new SharedCatalogLock(dataSource);
    }
    @AfterEach void clear() {
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);
        TransactionSynchronizationManager.clear();
    }
    @Test void nestedAcquisitionIsDeduplicatedAndRollbackReleasesTheSameConnection() throws Exception {
        lock.acquire(); lock.acquire();
        assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());
        verify(acquire,times(1)).executeQuery();
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(release).executeQuery(); verify(connection,never()).close();
    }
    @Test void timeoutDoesNotRegisterOrReleaseAnotherConnectionsLock() throws Exception {
        when(acquired.getInt(1)).thenReturn(0);
        BusinessException error=assertThrows(BusinessException.class,()->lock.acquire());
        assertEquals(1004,error.getErrorCode().getCode());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verify(release,never()).executeQuery();
    }
    @Test void failedReleaseInvalidatesTheConnectionInsteadOfReturningALeakedSessionLock() throws Exception {
        when(release.executeQuery()).thenThrow(new SQLException("simulated disconnect"));
        lock.acquire();
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(connection).abort(any(java.util.concurrent.Executor.class));
    }
    @Test void uncertainAcquisitionFailureAlsoInvalidatesTheConnection() throws Exception {
        when(acquire.executeQuery()).thenThrow(new SQLException("response lost after server acquisition"));
        assertThrows(BusinessException.class,()->lock.acquire());
        verify(connection).abort(any(java.util.concurrent.Executor.class));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }
}

