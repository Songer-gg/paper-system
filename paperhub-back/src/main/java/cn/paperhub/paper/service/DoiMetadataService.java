package cn.paperhub.paper.service;

import cn.paperhub.paper.vo.PaperMetadataView;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DoiMetadataService {
    private final CrossrefClient client;
    private final PaperValidation validation;
    private final Clock clock;
    private final boolean enabled;
    private final Semaphore tasks=new Semaphore(2);
    private final LinkedHashMap<String,Entry> cache=new LinkedHashMap<>(16,0.75f,true);
    public DoiMetadataService(CrossrefClient client,PaperValidation validation,Clock clock,
            @Value("${paperhub.metadata.crossref-enabled:true}") boolean enabled) {
        this.client=client;this.validation=validation;this.clock=clock;this.enabled=enabled;
    }
    public PaperMetadataView lookup(String value) {
        String doi=validation.normalizeDoi(value);
        if(doi==null) PaperValidation.invalid();
        if(!enabled) return empty(doi,"disabled","联网补全当前未启用，可继续本机识别或手动填写");
        synchronized(cache) {
            Entry entry=cache.get(doi);
            if(entry!=null && entry.expires>clock.millis()) return entry.value.copy();
            if(entry!=null) cache.remove(doi);
        }
        if(!tasks.tryAcquire()) return empty(doi,"busy","联网补全任务繁忙，可稍后重试或手动填写");
        try {
            JsonNode record=client.lookup(doi);
            PaperMetadataView result;
            if(record==null) result=empty(doi,"not-found","Crossref未找到该DOI记录，请核对DOI或手动填写");
            else result=map(doi,record);
            synchronized(cache) {
                cache.put(doi,new Entry(result.copy(),clock.millis()+Duration.ofMinutes(record==null?5:30).toMillis()));
                while(cache.size()>128) cache.remove(cache.keySet().iterator().next());
            }
            return result;
        } catch(IOException | RuntimeException failure) {
            return empty(doi,"unavailable","Crossref暂不可用或返回格式异常，已保留本机识别结果，可重试或手动填写");
        } finally { tasks.release(); }
    }
    private PaperMetadataView map(String doi,JsonNode record) throws IOException {
        String received=validation.normalizeDoi(record.path("DOI").asText(""));
        if(!doi.equals(received)) throw new IOException("Mismatched DOI record");
        PaperMetadataView result=new PaperMetadataView();result.lookupStatus="found";result.doi=doi;result.sources.put("doi","crossref");
        result.title=plain(first(record.path("title")),255);put(result,"title",result.title);
        result.journal=plain(first(record.path("container-title")),255);put(result,"journal",result.journal);
        JsonNode authors=record.path("author");
        if(authors.isArray()) for(JsonNode author:authors) {
            if(result.authors.size()>=50) { result.warnings.add("作者超过50位，仅保留前50位，请核对");break; }
            String name=author.path("literal").asText("");
            if(name.trim().isEmpty()) name=author.path("given").asText("")+" "+author.path("family").asText("");
            name=plain(name,120);
            if(!name.isEmpty() && !result.authors.contains(name) && result.authors.size()<50) result.authors.add(name);
        }
        if(!result.authors.isEmpty()) result.sources.put("authors","crossref");
        result.abstractText=MetadataPlainText.read(record.path("abstract").asText("")).replaceFirst("(?iu)^abstract\\b\\s*[:：]?\\s*","");
        if(result.abstractText.getBytes(StandardCharsets.UTF_8).length>60000) {
            result.abstractText="";result.warnings.add("Crossref摘要超过容量限制，请手动补充");
        }
        put(result,"abstract",result.abstractText);
        if(result.abstractText.isEmpty()) result.warnings.add("该DOI的Crossref记录未提供可用摘要，请手动补充或继续本机识别");
        for(String key:Arrays.asList("published-print","published-online","published","issued")) {
            JsonNode parts=record.path(key).path("date-parts");
            if(parts.isArray() && parts.size()>0 && parts.get(0).isArray() && parts.get(0).size()>0) {
                JsonNode year=parts.get(0).get(0);int candidate=year.asInt();
                if(year.isIntegralNumber() && year.canConvertToInt() && candidate>=1000 && candidate<=Year.now(clock).getValue()+1) {
                    result.year=candidate;result.sources.put("year","crossref");break;
                }
            }
        }
        return result;
    }
    private String plain(String value,int maximum) throws IOException {
        String clean=MetadataPlainText.read(value).replaceAll("\\s+"," ").trim();
        return clean.length()<=maximum?clean:"";
    }
    private String first(JsonNode values) { return values.isArray() && values.size()>0?values.get(0).asText(""):""; }
    private void put(PaperMetadataView result,String field,String value) { if(!value.isEmpty()) result.sources.put(field,"crossref"); }
    private PaperMetadataView empty(String doi,String status,String message) {
        PaperMetadataView result=new PaperMetadataView();result.doi=doi;result.lookupStatus=status;result.warnings.add(message);return result;
    }
    private static final class Entry {
        final PaperMetadataView value;final long expires;
        Entry(PaperMetadataView value,long expires) { this.value=value;this.expires=expires; }
    }
}
