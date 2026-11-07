package cn.paperhub.auth.vo;

import cn.paperhub.auth.entity.UserAccount;
import java.time.LocalDateTime;

public final class LoginUser {
    private final Long id;
    private final String username;
    private final String name;
    private final String email;
    private final String role;
    private final LocalDateTime registeredAt;

    public LoginUser(UserAccount account) {
        this.id = account.getId();
        this.username = account.getUsername();
        this.name = account.getName();
        this.email = account.getEmail();
        this.role = account.getRole();
        this.registeredAt = account.getRegisteredAt();
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
}
