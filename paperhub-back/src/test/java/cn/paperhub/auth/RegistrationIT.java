package cn.paperhub.auth;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.util.AopTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticationIT.Probes.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrationIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired PasswordEncoder encoder;
    @SpyBean AuditService audit;
    final AtomicInteger remote = new AtomicInteger();

    @BeforeAll void requireDisposableDatabase() {
        String expected = System.getenv("PAPERHUB_TEST_DATA_DIR");
        assertNotNull(expected, "Use scripts/verify_mysql.py; no mutations on an existing database");
        assertEquals(Paths.get(expected).toAbsolutePath().normalize(),
                Paths.get(jdbc.queryForObject("SELECT @@datadir", String.class)).toAbsolutePath().normalize());
    }
    @BeforeEach void freshWindow() { clock.advance(Duration.ofDays(2)); }
    @AfterEach void resetAudit() { reset(AopTestUtils.<AuditService>getUltimateTargetObject(audit)); }

    Map<String,Object> input() {
        String username = "reg_" + UUID.randomUUID().toString().replace("-", "");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("username", username); body.put("name", "新用户"); body.put("email", username + "@example.com");
        body.put("password", "注册密码123"); body.put("confirmPassword", "注册密码123");
        return body;
    }
    ResultActions register(Map<String,Object> body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(req -> {
            req.setRemoteAddr("192.0.2." + remote.incrementAndGet()); return req;
        }).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    long count(String username) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username=?", Long.class, username);
    }

    @Test void anonymousRegistrationNormalizesIdentityHashesPasswordAndCreatesAnEmptyOrdinaryAccount() throws Exception {
        Map<String,Object> body = input(); String username = (String) body.get("username");
        body.put("username", " " + username.toUpperCase(Locale.ROOT) + " ");
        body.put("email", " " + (username + "@EXAMPLE.COM").toUpperCase(Locale.ROOT) + " ");
        body.put("name", " 新用户 "); body.put("password", " 注册密码123 "); body.put("confirmPassword", " 注册密码123 ");
        String response = register(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.role").value("user")).andExpect(jsonPath("$.data.name").value("新用户"))
                .andReturn().getResponse().getContentAsString();
        JsonNode user = json.readTree(response).path("data"); assertEquals(6, user.size());
        assertFalse(user.has("password")); assertFalse(user.has("token")); assertFalse(user.has("tokenVersion"));
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM `user` WHERE username=?", username);
        assertEquals("active", row.get("status")); assertEquals(0, ((Number) row.get("token_version")).intValue());
        assertTrue(encoder.matches(" 注册密码123 ", (String) row.get("password")));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE user_id=? AND action='用户注册'", Long.class, row.get("id")));
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(new HashMap<String,Object>() {{ put("username", username+"@example.com"); put("password", " 注册密码123 "); put("role", "user"); }})))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).path("data").path("token").asText();
        mvc.perform(get("/api/v1/papers/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.privatePapers").isEmpty()).andExpect(jsonPath("$.data.sharedPapers").isEmpty());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test void duplicateUsernameAndEmailAreCaseInsensitiveAndDoNotWriteAnotherAccountOrAudit() throws Exception {
        Map<String,Object> first = input(); register(first).andExpect(status().isOk());
        Map<String,Object> second = input(); second.put("username", first.get("username").toString().toUpperCase(Locale.ROOT));
        register(second).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("用户名或邮箱已被使用"));
        second = input(); second.put("email", first.get("email").toString().toUpperCase(Locale.ROOT));
        register(second).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1004));
        assertEquals(1, count(first.get("username").toString()));
    }

    @Test void unknownPrivilegeFieldsAndInvalidCredentialsCannotCreateAnAccount() throws Exception {
        for (String field : Arrays.asList("role", "status", "id", "tokenVersion")) {
            Map<String,Object> body = input(); body.put(field, "admin");
            register(body).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
            assertEquals(0, count(body.get("username").toString()));
        }
        for (String username : Arrays.asList("ab", "1abc", "bad@example.com", "x';DROP TABLE user;--")) {
            Map<String,Object> body = input(); body.put("username", username);
            register(body).andExpect(status().isUnprocessableEntity());
        }
        Map<String,Object> body = input(); body.put("email", "not-an-email");
        register(body).andExpect(status().isUnprocessableEntity());
        body = input(); body.put("confirmPassword", "different");
        register(body).andExpect(status().isUnprocessableEntity());
    }

    @Test void passwordUsesUtf8BytesAndDoesNotSilentlyTruncate() throws Exception {
        for (String password : Arrays.asList("short", String.join("", Collections.nCopies(25, "密")), "        ", "password\0")) {
            Map<String,Object> body = input(); body.put("password", password); body.put("confirmPassword", password);
            register(body).andExpect(status().isUnprocessableEntity());
            assertEquals(0, count(body.get("username").toString()));
        }
        Map<String,Object> body = input(); String password = String.join("", Collections.nCopies(24, "密"));
        body.put("password", password); body.put("confirmPassword", password);
        register(body).andExpect(status().isOk());
        assertTrue(encoder.matches(password, jdbc.queryForObject("SELECT password FROM `user` WHERE username=?", String.class, body.get("username"))));
    }

    @Test void concurrentRegistrationsAreArbitratedByBothUniqueIndexes() throws Exception {
        for (String duplicateField : Arrays.asList("username", "email")) {
            Map<String,Object> first = input(), second = input(); second.put(duplicateField, first.get(duplicateField));
            CountDownLatch start = new CountDownLatch(1); ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> futures = new ArrayList<>();
                for (Map<String,Object> body : Arrays.asList(first, second)) futures.add(pool.submit(() -> {
                    start.await(); return register(body).andReturn().getResponse().getStatus();
                }));
                start.countDown(); List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : futures) statuses.add(result.get(20, TimeUnit.SECONDS));
                Collections.sort(statuses); assertEquals(Arrays.asList(200, 409), statuses);
            } finally { pool.shutdownNow(); }
        }
    }

    @Test void auditFailureRollsBackTheNewAccount() throws Exception {
        Map<String,Object> body = input();
        doThrow(new DataAccessResourceFailureException("registration-audit-probe"))
                .when(AopTestUtils.<AuditService>getUltimateTargetObject(audit)).mutation(any(), eq("用户注册"), anyString());
        String response = register(body).andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString();
        assertEquals(0, count(body.get("username").toString())); assertFalse(response.contains("registration-audit-probe"));
        assertFalse(response.contains(body.get("password").toString()));
    }

    @Test void directIpRateLimitIgnoresForwardedHeadersAndDoesNotConsumeLoginBudget() throws Exception {
        for (int i = 0; i < 6; i++) {
            Map<String,Object> body = input();
            ResultActions result = mvc.perform(post("/api/v1/auth/register").with(req -> { req.setRemoteAddr("198.51.100.88"); return req; })
                    .header("X-Forwarded-For", "192.0.2." + i).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
            if (i < 5) result.andExpect(status().isOk());
            else { result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1006)); assertEquals(0, count(body.get("username").toString())); }
        }
        mvc.perform(post("/api/v1/auth/login").with(req -> { req.setRemoteAddr("198.51.100.88"); return req; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin123\",\"role\":\"admin\"}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(2));
        mvc.perform(post("/api/v1/auth/register").with(req -> { req.setRemoteAddr("198.51.100.88"); return req; })
                .header("Authorization", "Bearer expired").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input())))
                .andExpect(status().isOk());
    }
}
