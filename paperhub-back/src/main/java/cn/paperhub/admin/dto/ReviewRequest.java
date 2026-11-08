package cn.paperhub.admin.dto;

import javax.validation.constraints.Size;

public class ReviewRequest {
    @Size(max=500) private String comment;
    private Boolean copyrightConfirmed;
    public String getComment() { return comment; }
    public void setComment(String value) { comment=value; }
    public Boolean getCopyrightConfirmed() { return copyrightConfirmed; }
    public void setCopyrightConfirmed(Boolean value) { copyrightConfirmed=value; }
}

