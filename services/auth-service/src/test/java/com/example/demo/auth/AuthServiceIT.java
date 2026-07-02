package com.example.demo.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.demo.common.security.Jwt;
import com.example.demo.common.json.Json;
import com.example.demo.auth.web.AuthDtos.LoginResponse;
import com.example.demo.auth.web.AuthDtos.UserResponse;

/**
 * Per-service integration test (MockMvc + real Postgres via Testcontainers). Flyway seeds the three
 * demo users (alice/bob/demo) with a bcrypt hash of {@code password}, so the existing login/user-list
 * behaviour is preserved while registration persists new users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthServiceIT {

    private static final String SECRET = "demo-secret-demo-secret-demo-secret-1234";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    // ---- login (existing) ----

    @Test
    void loginIssuesAToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("alice");
            softly.assertThat(response.token()).isNotBlank();
        });
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUserIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    // Blank username or password fails @NotBlank validation -> 400 (Spring default, no advice).
    @ParameterizedTest(name = "blank login field -> 400: {0}")
    @ValueSource(strings = {
        "{\"username\":\"\",\"password\":\"password\"}",
        "{\"username\":\"alice\",\"password\":\"\"}"
    })
    void rejectsBlankLoginFieldsWith400(String body) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- user list (existing) ----

    @Test
    void userListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userListIsReturnedWithAValidToken() throws Exception {
        String token = Jwt.issue("tester", SECRET, 300);

        String body = mockMvc.perform(get("/auth/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.readList(body, UserResponse.class))
                .extracting(UserResponse::username)
                .contains("alice", "bob", "demo");
    }

    @Test
    void getUserByIdReturnsTheUser() throws Exception {
        String token = Jwt.issue("tester", SECRET, 300);

        // u1 is alice's seeded id (V1__users.sql).
        String body = mockMvc.perform(get("/auth/users/u1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(Json.read(body, UserResponse.class).username()).isEqualTo("alice");
    }

    @Test
    void getUserByUnknownIdReturns404() throws Exception {
        String token = Jwt.issue("tester", SECRET, 300);

        mockMvc.perform(get("/auth/users/no-such-id").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anExpiredTokenIsRejectedWith401() throws Exception {
        String expired = Jwt.issue("tester", SECRET, -60); // already expired

        mockMvc.perform(get("/auth/users").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // ---- registration ----

    @Test
    void registerCreatesUserAndReturnsToken() throws Exception {
        String body = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\",\"email\":\"carol@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("carol");
            softly.assertThat(response.token()).isNotBlank();
        });
    }

    @Test
    void registeredUserCanThenLogIn() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dave\",\"email\":\"dave@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dave\",\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        LoginResponse response = Json.read(body, LoginResponse.class);
        assertSoftly(softly -> {
            softly.assertThat(response.username()).isEqualTo("dave");
            softly.assertThat(response.token()).isNotBlank();
        });
    }

    @Test
    void duplicateUsernameIsConflict() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"erin\",\"email\":\"erin@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"erin\",\"email\":\"erin2@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"frank\",\"email\":\"shared@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"grace\",\"email\":\"shared@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isConflict());
    }

    // Registering an already-seeded username (alice) is a conflict — proves the Flyway seed is live.
    @Test
    void registeringSeededUsernameIsConflict() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice.new@demo.example.com\","
                                + "\"password\":\"s3cret-pw\"}"))
                .andExpect(status().isConflict());
    }

    // EP/BVA invalid partitions: blank/short/too-long/bad-pattern username, bad/blank email, short/blank
    // password. Each fails bean validation -> 400.
    @ParameterizedTest(name = "invalid registration -> 400: {0}")
    @ValueSource(strings = {
        "{\"username\":\"\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"ab\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"this_username_is_way_too_long\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"bad name\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"bad-hyphen\",\"email\":\"a@b.com\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"not-an-email\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"\",\"password\":\"s3cret-pw\"}",
        "{\"username\":\"good\",\"email\":\"a@b.com\",\"password\":\"short\"}",
        "{\"username\":\"good\",\"email\":\"a@b.com\",\"password\":\"\"}"
    })
    void rejectsInvalidRegistrationWith400(String body) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
