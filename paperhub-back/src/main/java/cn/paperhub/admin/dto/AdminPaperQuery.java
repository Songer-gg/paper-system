package cn.paperhub.admin.dto;

import javax.validation.constraints.*;

public class AdminPaperQuery {
    @Size(max=255) private String title;
    @Size(max=120) private String doi;
    @Size(max=50) private String uploader;
    private Long categoryId;
    @Pattern(regexp="all|pending|approved|rejected|offline") private String status;
    public String getTitle() { return title; }
    public void setTitle(String value) { title=value; }
    public String getDoi() { return doi; }
    public void setDoi(String value) { doi=value; }
    public String getUploader() { return uploader; }
    public void setUploader(String value) { uploader=value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
}

