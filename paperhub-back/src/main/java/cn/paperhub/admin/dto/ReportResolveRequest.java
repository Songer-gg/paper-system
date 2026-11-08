package cn.paperhub.admin.dto;

import javax.validation.constraints.*;

public class ReportResolveRequest {
    @NotNull private String result;
    @NotNull private Boolean offlinePaperToo;
    public String getResult() { return result; }
    public void setResult(String value) { result=value; }
    public Boolean getOfflinePaperToo() { return offlinePaperToo; }
    public void setOfflinePaperToo(Boolean value) { offlinePaperToo=value; }
}
