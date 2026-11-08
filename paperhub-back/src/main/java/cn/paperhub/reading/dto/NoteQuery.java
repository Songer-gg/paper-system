package cn.paperhub.reading.dto;

import javax.validation.constraints.Size;

public class NoteQuery {
    private Long paperId;
    @Size(max=600) private String keyword;
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long value) { paperId=value; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword=value; }
}

