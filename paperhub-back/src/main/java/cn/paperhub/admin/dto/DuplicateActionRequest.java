package cn.paperhub.admin.dto;

import javax.validation.constraints.Size;

public class DuplicateActionRequest {
    @Size(max=500) private String note;
    public String getNote() { return note; }
    public void setNote(String value) { note=value; }
}

