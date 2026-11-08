package cn.paperhub.security;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean public WebSecurityCustomizer rejectedRequestHandler(SecurityResponseWriter writer) {
        return web -> web.requestRejectedHandler((request, response, exception) ->
                writer.write(response, ErrorCode.INVALID_PARAM));
    }

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationFilter jwt,
            SecurityResponseWriter writer) throws Exception {
        http.csrf().disable().cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .requestCache().disable()
                .formLogin().disable().httpBasic().disable().logout().disable()
                .exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> writer.write(res, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> writer.write(res, ErrorCode.FORBIDDEN)).and()
                .authorizeRequests()
                .antMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .antMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .antMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll().and()
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsFilter corsFilter(SecurityProperties properties, ObjectMapper mapper) {
        List<String> origins = new ArrayList<>();
        for (String origin : properties.getAllowedOrigins()) {
            if (origin == null || origin.trim().isEmpty()) continue;
            if (!(origin.startsWith("http://") || origin.startsWith("https://")) || origin.contains("*")) {
                throw new IllegalStateException("CORS requires explicit HTTP/HTTPS origins");
            }
            origins.add(origin);
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(false);
        cors.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        cors.setExposedHeaders(Arrays.asList("Content-Disposition"));
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        CorsFilter filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                mapper.writeValue(response.getBody(), ApiResponse.failure(ErrorCode.FORBIDDEN));
            }
        });
        return filter;
    }

    // Security owns these filters; prevent a second servlet-container registration.
    @Bean public FilterRegistrationBean<JwtAuthenticationFilter> jwtServletRegistration(JwtAuthenticationFilter jwt) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwt);
        registration.setEnabled(false);
        return registration;
    }
    @Bean public FilterRegistrationBean<CorsFilter> corsServletRegistration(CorsFilter cors) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(cors);
        registration.setEnabled(false);
        return registration;
    }
}
