package cn.paperhub.admin.dto;

import javax.validation.constraints.Size;

public class RejectRequest {
    @Size(max=500) private String comment;
    public String getComment() { return comment; }
    public void setComment(String value) { comment=value; }
}
