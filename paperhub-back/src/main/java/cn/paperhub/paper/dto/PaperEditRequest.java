package cn.paperhub.paper.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.*;

public class PaperEditRequest {
    private final Set<String> supplied = new HashSet<>();
    private String title;
    private List<String> authors;
    private String abstractText;
    private String doi;
    private String journal;
    private Integer year;
    private List<String> keywords;
    private Long categoryId;
    private List<Long> tagIds;
    private String link;
    @JsonIgnore public boolean supplied(String field) { return supplied.contains(field); }
    @JsonIgnore public boolean isEmpty() { return supplied.isEmpty(); }
    public String getTitle() { return title; }
    @JsonSetter("title")
    public void setTitle(String value) { supplied.add("title"); this.title = value; }
    public List<String> getAuthors() { return authors; }
    @JsonSetter("authors")
    public void setAuthors(List<String> value) { supplied.add("authors"); this.authors = value; }
    public String getAbstractText() { return abstractText; }
    @JsonSetter("abstract")
    public void setAbstractText(String value) { supplied.add("abstractText"); this.abstractText = value; }
    public String getDoi() { return doi; }
    @JsonSetter("doi")
    public void setDoi(String value) { supplied.add("doi"); this.doi = value; }
    public String getJournal() { return journal; }
    @JsonSetter("journal")
    public void setJournal(String value) { supplied.add("journal"); this.journal = value; }
    public Integer getYear() { return year; }
    @JsonSetter("year")
    public void setYear(Integer value) { supplied.add("year"); this.year = value; }
    public List<String> getKeywords() { return keywords; }
    @JsonSetter("keywords")
    public void setKeywords(List<String> value) { supplied.add("keywords"); this.keywords = value; }
    public Long getCategoryId() { return categoryId; }
    @JsonSetter("categoryId")
    public void setCategoryId(Long value) { supplied.add("categoryId"); this.categoryId = value; }
    public List<Long> getTagIds() { return tagIds; }
    @JsonSetter("tagIds")
    public void setTagIds(List<Long> value) { supplied.add("tagIds"); this.tagIds = value; }
    public String getLink() { return link; }
    @JsonSetter("link")
    public void setLink(String value) { supplied.add("link"); this.link = value; }
}

