package cn.paperhub.auth;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.security.SessionPrincipal;
import cn.paperhub.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"paperhub.security.allowed-origins=http://localhost:5173"})
@AutoConfigureMockMvc
@Import(AuthenticationIT.Probes.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationIT {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    @BeforeAll void requireIsolatedMySql() {
        String expected = System.getenv("PAPERHUB_TEST_DATA_DIR");
        assertNotNull(expected, "Use scripts/verify_mysql.py; integration tests must not modify an existing database");
        String actual = jdbc.queryForObject("SELECT @@datadir", String.class);
        assertEquals(Paths.get(expected).toAbsolutePath().normalize(), Paths.get(actual).toAbsolutePath().normalize());
        assertEquals("+08:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
    }
    @BeforeEach void resetFixtures() {
        clock.advance(Duration.ofDays(2));
        jdbc.update("UPDATE `user` SET status='active', role='user', token_version=0 WHERE id=1");
        jdbc.update("UPDATE `user` SET status='active', role='admin', token_version=0 WHERE id=101");
    }
    private ResultActions login(String username, String password, String role) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginBody(username, password, role))));
    }
    private String token(String username, String password, String role) throws Exception {
        String body = login(username, password, role).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    @Test void usernameAndEmailLoginMatchFrontendShapeWithoutLeakingSecrets() throws Exception {
        String body = login("researcher", "123456", "user").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.registeredAt").value("2025-03-12 09:30:00"))
                .andReturn().getResponse().getContentAsString();
        JsonNode user = json.readTree(body).path("data").path("user");
        assertEquals(6, user.size());
        assertFalse(user.has("password"));
        assertFalse(user.has("tokenVersion"));
        assertFalse(body.contains("$2b$"));
        assertFalse(token("researcher@paperhub.cn", "123456", "user").isEmpty());
        assertFalse(token("admin", "admin123", "admin").isEmpty());
    }
    @Test void wrongCredentialsRoleAndDisabledAccountsShareTheSameError() throws Exception {
        String absent = login("nobody", "wrong", "user").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(1005)).andReturn().getResponse().getContentAsString();
        String role = login("researcher", "123456", "admin").andReturn().getResponse().getContentAsString();
        String wrong = login("researcher", "wrong", "user").andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE `user` SET status='disabled' WHERE id=1");
        String disabled = login("researcher", "123456", "user").andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(absent), json.readTree(role));
        assertEquals(json.readTree(absent), json.readTree(wrong));
        assertEquals(json.readTree(absent), json.readTree(disabled));
    }
    @Test void anonymousAndInvalidBearerRequestsReturnJson401() throws Exception {
        mvc.perform(get("/api/v1/test/principal")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001)).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Basic anything"))
                .andExpect(status().isUnauthorized());
    }
    @Test void rolesAreSeparatedAndQueryCannotChangePrincipal() throws Exception {
        String user = token("researcher", "123456", "user");
        mvc.perform(get("/api/v1/admin/test/protected").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1002));
        mvc.perform(get("/api/v1/test/principal").param("userId", "101").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
        String admin = token("admin", "admin123", "admin");
        mvc.perform(get("/api/v1/admin/test/protected").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }
    @Test void disablingAndReenablingAccountDoesNotReviveOldToken() throws Exception {
        String token = token("researcher", "123456", "user");
        jdbc.update("UPDATE `user` SET status='disabled', token_version=token_version+1 WHERE id=1");
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE `user` SET status='active' WHERE id=1");
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void passwordResetOrRoleChangeRevokesExistingToken() throws Exception {
        String token = token("researcher", "123456", "user");
        jdbc.update("UPDATE `user` SET token_version=token_version+1 WHERE id=1");
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        String replacement = token("researcher", "123456", "user");
        jdbc.update("UPDATE `user` SET role='admin' WHERE id=1");
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + replacement))
                .andExpect(status().isUnauthorized());
    }
    @Test void expirationIsChecked() throws Exception {
        String token = token("researcher", "123456", "user");
        clock.advance(Duration.ofHours(25));
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void malformedUnknownAndInvalidFieldsUseUnifiedValidationResponse() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"researcher\",\"password\":\"123456\",\"role\":\"user\",\"userId\":101}"))
                .andExpect(status().isUnprocessableEntity());
        login("", "password", "owner").andExpect(status().isUnprocessableEntity());
    }
    @Test void corsAllowsOnlyConfiguredOriginsAndDoesNotRequireTokenForPreflight() throws Exception {
        mvc.perform(options("/api/v1/test/principal").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/test/principal").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1002));
    }
    @Test void loginRateIsBoundedAndDuplicateAuthorizationHeadersAreRejected() throws Exception {
        for (int i = 0; i < 10; i++) login("missing", "wrong", "user").andExpect(status().isUnprocessableEntity());
        login("missing", "wrong", "user").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1006));
        clock.advance(Duration.ofMinutes(2));
        String token = token("researcher", "123456", "user");
        mvc.perform(get("/api/v1/test/principal").header("Authorization", "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void authenticatedMissingEndpointUsesUnified404() throws Exception {
        String token = token("researcher", "123456", "user");
        mvc.perform(get("/api/v1/not-implemented").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1003));
    }

    @Test void firewallRejectionsAlsoUseJsonAndLoginIgnoresAnExpiredAuthorizationHeader() throws Exception {
        mvc.perform(get("/api/v1/test/principal;ignored"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
        mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer expired")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"researcher\",\"password\":\"123456\",\"role\":\"user\"}"))
                .andExpect(status().isOk());
    }

    @Test void internalErrorsDoNotExposeExceptionDetails() throws Exception {
        String token = token("researcher", "123456", "user");
        String body = mvc.perform(get("/api/v1/test/failure").header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(1500))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("test-internal-detail"));
        assertFalse(body.contains("IllegalStateException"));
    }

    private static final class LoginBody {
        public final String username;
        public final String password;
        public final String role;
        private LoginBody(String username, String password, String role) {
            this.username = username; this.password = password; this.role = role;
        }
    }
    @TestConfiguration
    static class Probes {
        @Bean @Primary MutableClock integrationClock() { return new MutableClock(); }
        @Bean ProbeController probeController() { return new ProbeController(); }
    }
    @RestController
    @TestComponent
    static class ProbeController {
        @GetMapping("/api/v1/test/principal")
        ApiResponse<?> principal(@AuthenticationPrincipal SessionPrincipal principal) {
            return ApiResponse.success(Collections.singletonMap("id", principal.getId()));
        }
        @GetMapping("/api/v1/admin/test/protected")
        ApiResponse<?> admin() { return ApiResponse.success(true); }
        @GetMapping("/api/v1/test/failure")
        ApiResponse<?> failure() { throw new IllegalStateException("test-internal-detail"); }
    }
}
