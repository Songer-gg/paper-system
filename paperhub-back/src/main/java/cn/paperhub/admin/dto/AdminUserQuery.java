package cn.paperhub.admin.dto;

import javax.validation.constraints.*;

public class AdminUserQuery {
    @Size(max=240) private String keyword;
    @NotNull @Pattern(regexp="all|active|disabled") private String status="all";
    @Size(max=10) private String dateFrom,dateTo;
    public String getKeyword() { return keyword; }
    public void setKeyword(String value) { keyword=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String value) { dateFrom=value; }
    public String getDateTo() { return dateTo; }
    public void setDateTo(String value) { dateTo=value; }
}
