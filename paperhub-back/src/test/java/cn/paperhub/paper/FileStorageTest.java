package cn.paperhub.paper;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.common.storage.LocalFileStorage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class FileStorageTest {
    @TempDir Path directory;
    @Test void actualStreamLimitCannotBeBypassedWithAForgedDeclaredSize() throws Exception {
        LocalFileStorage storage=new LocalFileStorage(directory.toString());
        MockMultipartFile lying=new MockMultipartFile("file","file.pdf","application/pdf",new byte[]{1}) {
            @Override public long getSize() { return 1; }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    long remaining=LocalFileStorage.PDF_LIMIT+1;
                    @Override public int read() { return remaining-- > 0 ? 0 : -1; }
                    @Override public int read(byte[] buffer,int offset,int length) {
                        if(remaining<=0) return -1;
                        int count=(int)Math.min(remaining,length); Arrays.fill(buffer,offset,offset+count,(byte)0);
                        remaining-=count; return count;
                    }
                };
            }
        };
        BusinessException error=assertThrows(BusinessException.class,()->storage.pdf(lying));
        assertTrue(error.getMessage().contains("100 MiB"));
        try(Stream<Path> files=Files.list(directory.resolve("papers"))) { assertEquals(0,files.count()); }
        try(Stream<Path> jobs=Files.list(directory.resolve(".cleanup"))) { assertEquals(0,jobs.count()); }
    }
    @Test void partialStreamFailureLeavesNoFileAndPathsAreConstrained() throws Exception {
        LocalFileStorage storage=new LocalFileStorage(directory.toString());
        MockMultipartFile broken=new MockMultipartFile("file","file.pdf","application/pdf",new byte[]{1}) {
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("simulated broken stream"); }
                };
            }
        };
        assertThrows(IOException.class,()->storage.pdf(broken));
        for(String path:Arrays.asList("../pom.xml","C:/secret.pdf","/storage/2001.pdf","papers/../../secret.pdf"))
            assertThrows(IOException.class,()->storage.open(path));
        try(Stream<Path> files=Files.list(directory.resolve("papers"))) { assertEquals(0,files.count()); }
    }
}

