package cn.paperhub.reading.dto;

import javax.validation.constraints.*;

public class ReadingStatusRequest {
    @NotNull @Pattern(regexp="unread|reading|read|paused")
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
}

