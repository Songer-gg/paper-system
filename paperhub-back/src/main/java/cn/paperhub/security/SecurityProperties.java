package cn.paperhub.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "paperhub.security")
public class SecurityProperties {
    @NotBlank
    private String jwtSecret;
    @NotNull
    private Duration tokenTtl = Duration.ofHours(24);
    private List<String> allowedOrigins = new ArrayList<>();
    @Valid @NotNull
    private Login login = new Login();

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Login getLogin() { return login; }
    public void setLogin(Login login) { this.login = login; }

    public static class Login {
        @NotNull private Duration window = Duration.ofMinutes(1);
        @Min(1) private int perIp = 20;
        @Min(1) private int perAccount = 10;
        @Min(2) private int maxEntries = 10000;
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
        public int getPerIp() { return perIp; }
        public void setPerIp(int perIp) { this.perIp = perIp; }
        public int getPerAccount() { return perAccount; }
        public void setPerAccount(int perAccount) { this.perAccount = perAccount; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }
}
