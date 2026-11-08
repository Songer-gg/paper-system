package cn.paperhub.paper.service;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.common.storage.LocalFileStorage;
import cn.paperhub.paper.vo.PaperMetadataView;
import cn.paperhub.support.MetadataPdf;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaperMetadataServiceTest {
    @TempDir Path root;
    @Test void busyWorkersKeepTheirSlotsAfterCallersStopWaitingAndReleaseAfterCleanup() throws Exception {
        PaperMetadataExtractor extractor=mock(PaperMetadataExtractor.class);
        CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1),exited=new CountDownLatch(2);
        when(extractor.extract(any(),anyLong())).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS));return new PaperMetadataView();
        });
        PaperMetadataService service=new PaperMetadataService(new LocalFileStorage(root.toString()),extractor);
        ExecutorService clients=Executors.newFixedThreadPool(2);
        MockMultipartFile file=new MockMultipartFile("file",MetadataPdf.paper(true));
        try {
            Future<?> first=clients.submit(()->{ try { service.parse(file); } finally { exited.countDown(); } });
            Future<?> second=clients.submit(()->{ try { service.parse(file); } finally { exited.countDown(); } });
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            assertEquals(1006,assertThrows(BusinessException.class,()->service.parse(file)).getErrorCode().getCode());
            first.cancel(true);second.cancel(true);assertTrue(exited.await(3,TimeUnit.SECONDS));
            assertThrows(BusinessException.class,()->service.parse(file));
            release.countDown();
            boolean recovered=false;
            for(int i=0;i<100;i++) {
                try { service.parse(file);recovered=true;break; }
                catch(BusinessException busy) { Thread.sleep(10); }
            }
            assertTrue(recovered,"Finished workers must return permits");
        } finally { release.countDown();clients.shutdownNow();service.close(); }
    }
    @Test void actualBytesAreBoundedEvenWhenTheReportedSizeIsWrong() throws Exception {
        PaperMetadataService service=new PaperMetadataService(new LocalFileStorage(root.toString()),mock(PaperMetadataExtractor.class));
        MultipartFile file=mock(MultipartFile.class);when(file.isEmpty()).thenReturn(false);when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[(int)PaperMetadataService.LIMIT+1]));
        try { assertEquals(1005,assertThrows(BusinessException.class,()->service.parse(file)).getErrorCode().getCode()); }
        finally { service.close(); }
    }
    @Test void shutdownRejectsNewTasksWithoutLeakingSlots() throws Exception {
        PaperMetadataService service=new PaperMetadataService(new LocalFileStorage(root.toString()),mock(PaperMetadataExtractor.class));
        service.close();MockMultipartFile file=new MockMultipartFile("file",MetadataPdf.paper(true));
        for(int i=0;i<3;i++) assertEquals(1006,assertThrows(BusinessException.class,()->service.parse(file)).getErrorCode().getCode());
    }
}
