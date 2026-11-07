package cn.paperhub.auth.vo;

public final class LoginResponse {
    private final String token;
    private final LoginUser user;
    public LoginResponse(String token, LoginUser user) { this.token = token; this.user = user; }
    public String getToken() { return token; }
    public LoginUser getUser() { return user; }
}
