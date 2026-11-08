package cn.paperhub.reading.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.HashSet;
import java.util.Set;

public class NotePatchRequest {
    private final Set<String> supplied=new HashSet<>();
    private Long paperId;
    private String title,content;
    public Long getPaperId() { return paperId; }
    @JsonSetter("paperId") public void setPaperId(Long value) { supplied.add("paperId"); paperId=value; }
    public String getTitle() { return title; }
    @JsonSetter("title") public void setTitle(String value) { supplied.add("title"); title=value; }
    public String getContent() { return content; }
    @JsonSetter("content") public void setContent(String value) { supplied.add("content"); content=value; }
    public boolean has(String field) { return supplied.contains(field); }
    public boolean empty() { return supplied.isEmpty(); }
}

