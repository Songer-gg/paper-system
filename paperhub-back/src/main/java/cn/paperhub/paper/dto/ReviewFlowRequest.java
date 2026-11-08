package cn.paperhub.paper.dto;

import javax.validation.constraints.*;

public class ReviewFlowRequest {
    @NotNull @Pattern(regexp="share|unshare|resubmit")
    private String action;
    private Boolean copyrightAgreed;
    public String getAction() { return action; }
    public void setAction(String value) { action=value; }
    public Boolean getCopyrightAgreed() { return copyrightAgreed; }
    public void setCopyrightAgreed(Boolean value) { copyrightAgreed=value; }
}

