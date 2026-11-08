package cn.paperhub.paper.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

/** Candidate metadata only; never an upload authorization or a persisted paper. */
public class PaperMetadataView {
    public String title = "";
    public List<String> authors = new ArrayList<>();
    @JsonProperty("abstract") public String abstractText = "";
    public String doi = "";
    public String journal = "";
    public Integer year;
    public List<String> keywords = new ArrayList<>();
    public int pageCount;
    public int parsedPages;
    public Map<String,String> sources = new LinkedHashMap<>();
    public List<String> warnings = new ArrayList<>();
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String lookupStatus;

    public PaperMetadataView copy() {
        PaperMetadataView result = new PaperMetadataView();
        result.title=title;result.authors=new ArrayList<>(authors);result.abstractText=abstractText;
        result.doi=doi;result.journal=journal;result.year=year;result.keywords=new ArrayList<>(keywords);
        result.pageCount=pageCount;result.parsedPages=parsedPages;result.sources=new LinkedHashMap<>(sources);
        result.warnings=new ArrayList<>(warnings);result.lookupStatus=lookupStatus;return result;
    }
}
