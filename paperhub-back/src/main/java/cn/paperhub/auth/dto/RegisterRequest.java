package cn.paperhub.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank @Size(max = 100) private String username;
    @NotBlank @Size(max = 100) private String name;
    @NotBlank @Size(max = 240) private String email;
    @NotBlank @Size(max = 72) private String password;
    @NotBlank @Size(max = 72) private String confirmPassword;

    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String value) { confirmPassword = value; }
}
