package cn.paperhub.paper.dto;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public class UploadRequest extends PaperEditRequest {
    private String visibility;
    private Boolean copyrightAgreed;
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Boolean getCopyrightAgreed() { return copyrightAgreed; }
    public void setCopyrightAgreed(Boolean copyrightAgreed) { this.copyrightAgreed = copyrightAgreed; }

}

