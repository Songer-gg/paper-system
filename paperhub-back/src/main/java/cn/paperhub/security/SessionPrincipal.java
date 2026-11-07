package cn.paperhub.security;

import java.security.Principal;

public final class SessionPrincipal implements Principal {
    private final long id;
    private final String username;
    private final String role;

    public SessionPrincipal(long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }
    public long getId() { return id; }
    public String getRole() { return role; }
    @Override public String getName() { return username; }
}
