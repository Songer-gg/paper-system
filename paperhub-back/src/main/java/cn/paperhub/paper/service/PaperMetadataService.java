package cn.paperhub.paper.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.common.storage.LocalFileStorage;
import cn.paperhub.paper.vo.PaperMetadataView;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Temporary parse only. Permits stay owned by the worker even after the HTTP caller times out. */
@Service
public class PaperMetadataService {
    public static final long LIMIT = 20L * 1024 * 1024;
    private final Semaphore slots = new Semaphore(2);
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "paperhub-pdf-metadata"); thread.setDaemon(true); return thread;
    });
    private final LocalFileStorage storage;
    private final PaperMetadataExtractor extractor;
    public PaperMetadataService(LocalFileStorage storage, PaperMetadataExtractor extractor) {
        this.storage=storage; this.extractor=extractor;
    }
    public PaperMetadataView parse(MultipartFile file) {
        if (file.isEmpty() || file.getSize()>LIMIT) invalid("自动解析支持20MiB以内的PDF；较大文件可手动填写后提交");
        if (!slots.tryAcquire()) throw new BusinessException(ErrorCode.BIZ_REJECTED,"解析任务繁忙，请稍后重试或手动填写");
        CompletableFuture<PaperMetadataView> result = new CompletableFuture<>();
        boolean handedOff = false;
        try {
            byte[] bytes = boundedBytes(file);
            if (bytes.length<5 || !Arrays.equals(Arrays.copyOf(bytes,5),new byte[]{'%', 'P', 'D', 'F', '-'})) invalid("文件内容不是有效PDF");
            workers.execute(() -> {
                try {
                    if (result.isCancelled()) return;
                    long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    PaperMetadataView parsed;
                    try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes),
                            MemoryUsageSetting.setupMixed(8L*1024*1024,100L*1024*1024).setTempDir(storage.root().resolve(".scratch").toFile()))) {
                        if (document.isEncrypted() || document.getNumberOfPages()<1 || document.getNumberOfPages()>65535)
                            invalid("PDF加密、为空或页数超出范围，请使用可读取的PDF");
                        parsed = extractor.extract(document,deadline);
                    }
                    result.complete(parsed);
                } catch (Exception ex) {
                    result.completeExceptionally(ex instanceof BusinessException ? ex
                            : new BusinessException(ErrorCode.INVALID_PARAM,"PDF无法解析，请检查文件或手动填写"));
                } finally { slots.release(); }
            });
            handedOff = true;
            return result.get(20,TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            result.cancel(false); throw new BusinessException(ErrorCode.BIZ_REJECTED,"解析超时，请手动填写或稍后重试");
        } catch (InterruptedException ex) {
            result.cancel(false); Thread.currentThread().interrupt(); throw new BusinessException(ErrorCode.BIZ_REJECTED,"解析已中断，请重试");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof BusinessException) throw (BusinessException)ex.getCause();
            throw new BusinessException(ErrorCode.INVALID_PARAM,"PDF无法解析，请手动填写");
        } catch (IOException ex) { throw new BusinessException(ErrorCode.INVALID_PARAM,"无法读取PDF，请重新选择文件"); }
        catch (RejectedExecutionException ex) { throw new BusinessException(ErrorCode.BIZ_REJECTED,"解析暂不可用，请手动填写"); }
        finally { if (!handedOff) slots.release(); }
    }
    private byte[] boundedBytes(MultipartFile file) throws IOException {
        try (InputStream input=file.getInputStream(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int read;
            while ((read=input.read(buffer))!=-1) {
                if ((long)output.size()+read>LIMIT) invalid("自动解析文件超过20MiB，请手动填写");
                output.write(buffer,0,read);
            }
            return output.toByteArray();
        }
    }
    private void invalid(String message) { throw new BusinessException(ErrorCode.INVALID_PARAM,message); }
    @PreDestroy public void close() { workers.shutdownNow(); }
}
