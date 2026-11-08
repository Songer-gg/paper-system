package cn.paperhub.support;

import cn.paperhub.paper.service.PaperMetadataExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Local diagnostic entry point, test classpath only; never bundled in the executable application. */
public final class MetadataInspector {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Expected input PDF and output JSON");
        try(PDDocument document=PDDocument.load(new File(args[0]),MemoryUsageSetting.setupMixed(16L*1024*1024,200L*1024*1024))) {
            new ObjectMapper().writeValue(new File(args[1]),new PaperMetadataExtractor(Clock.systemDefaultZone())
                    .extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10)));
        }
    }
}
