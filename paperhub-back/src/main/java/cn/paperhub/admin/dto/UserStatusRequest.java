package cn.paperhub.admin.dto;

import javax.validation.constraints.*;

public class UserStatusRequest {
    @NotNull @Pattern(regexp="active|disabled") private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
}
