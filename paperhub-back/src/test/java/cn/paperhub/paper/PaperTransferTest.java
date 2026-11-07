package cn.paperhub.paper;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.paper.controller.PaperController;
import cn.paperhub.paper.service.PaperService;
import cn.paperhub.paper.vo.PaperFile;
import cn.paperhub.security.SessionPrincipal;
import java.io.*;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperTransferTest {
    @Test void failedTransferClosesInputAndRecordsFailureWithoutClaimingDatabaseRollback() throws Exception {
        PaperService service=mock(PaperService.class); AuditService audit=mock(AuditService.class);
        SessionPrincipal admin=new SessionPrincipal(101,"admin","admin");
        InputStream input=spy(new ByteArrayInputStream(new byte[]{1,2,3}));
        when(service.file(42,true,admin)).thenReturn(new PaperFile(input,3,"paper",42,1,true));
        HttpServletResponse response=mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) {}
            @Override public void write(int b) throws IOException { throw new IOException("disconnected client"); }
        });
        assertThrows(IOException.class,()->new PaperController(service,audit).download(42,admin,response));
        verify(audit).sensitive(admin,42,1,"fileTransferFailed",false);
        verify(input).close(); verify(response).reset();
    }
}

