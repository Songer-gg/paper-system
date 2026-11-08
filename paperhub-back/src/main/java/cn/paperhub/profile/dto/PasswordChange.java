package cn.paperhub.profile.dto;

import javax.validation.constraints.*;

public class PasswordChange {
    @NotNull @Size(max=72) private String oldPassword;
    @NotNull @Size(max=72) private String newPassword;
    @NotNull @Size(max=72) private String confirmPassword;
    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String value) { oldPassword=value; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String value) { newPassword=value; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String value) { confirmPassword=value; }
}
