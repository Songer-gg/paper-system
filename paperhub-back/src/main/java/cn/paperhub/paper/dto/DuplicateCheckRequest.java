package cn.paperhub.paper.dto;

import javax.validation.constraints.*;

public class DuplicateCheckRequest {
    @Size(max=255) private String title;
    @Size(max=120) private String doi;
    @Pattern(regexp="(?i)[0-9a-f]{64}") private String fileSha256;
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getDoi() { return doi; }
    public void setDoi(String value) { doi=value; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String value) { fileSha256=value; }
}

