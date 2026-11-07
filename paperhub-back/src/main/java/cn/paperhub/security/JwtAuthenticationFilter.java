package cn.paperhub.security;

import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.common.api.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwt;
    private final UserAccountMapper users;
    private final SecurityResponseWriter writer;

    public JwtAuthenticationFilter(JwtService jwt, UserAccountMapper users, SecurityResponseWriter writer) {
        this.jwt = jwt;
        this.users = users;
        this.writer = writer;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Enumeration<String> headers = request.getHeaders("Authorization");
            int count = 0;
            while (headers.hasMoreElements()) { headers.nextElement(); count++; }
            if (count != 1 || header.length() > 8192 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                    || header.length() <= 7 || header.substring(7).matches(".*\\s.*")) {
                throw new JwtException("Invalid authorization header");
            }
            Claims claims = jwt.parse(header.substring(7));
            long id = Long.parseLong(claims.getSubject());
            Number version = claims.get("ver", Number.class);
            if (id < 1 || id > JwtService.MAX_SAFE_ID || version == null
                    || version.doubleValue() != version.longValue() || version.longValue() < 0) {
                throw new JwtException("Invalid session identity");
            }
            UserAccount account = users.selectById(id);
            if (account == null || !"active".equals(account.getStatus())
                    || !Long.valueOf(version.longValue()).equals(account.getTokenVersion())
                    || !account.getRole().equals(claims.get("role", String.class))
                    || !("user".equals(account.getRole()) || "admin".equals(account.getRole()))) {
                throw new JwtException("Session no longer valid");
            }
            SessionPrincipal principal = new SessionPrincipal(id, account.getUsername(), account.getRole());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, Collections.singletonList(new SimpleGrantedAuthority(
                            "ROLE_" + account.getRole().toUpperCase(Locale.ROOT))));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writer.write(response, ErrorCode.UNAUTHORIZED);
            return;
        } catch (DataAccessException ex) {
            SecurityContextHolder.clearContext();
            LOG.error("Session database verification failed");
            writer.write(response, ErrorCode.SERVER_ERROR);
            return;
        }
        chain.doFilter(request, response);
    }
}
