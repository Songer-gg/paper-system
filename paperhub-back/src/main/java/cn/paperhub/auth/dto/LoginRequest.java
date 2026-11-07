package cn.paperhub.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class LoginRequest {
    @NotBlank @Size(max = 120)
    private String username;
    @NotBlank @Size(max = 72)
    private String password;
    @NotBlank @Pattern(regexp = "user|admin")
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
