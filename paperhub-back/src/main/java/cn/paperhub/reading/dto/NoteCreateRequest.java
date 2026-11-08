package cn.paperhub.reading.dto;

import javax.validation.constraints.*;

public class NoteCreateRequest {
    @NotNull private Long paperId;
    @NotNull private String title;
    @NotNull private String content;
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getContent() { return content; }
    public void setContent(String value) { content=value; }
}

