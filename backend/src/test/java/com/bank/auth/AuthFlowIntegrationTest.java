package com.bank.auth;

import com.bank.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode register(String email) throws Exception {
        String body = """
                {"email":"%s","password":"Secret123","firstName":"Ada","lastName":"Lovelace"}
                """.formatted(email);
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void registerThenFetchProfile() throws Exception {
        JsonNode tokens = register("ada@example.com");
        String access = tokens.get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("ada@example.com")))
                .andExpect(jsonPath("$.kycStatus", is("PENDING")))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        register("grace@example.com");
        String body = """
                {"email":"grace@example.com","password":"WrongPass1"}
                """;
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRotates() throws Exception {
        JsonNode tokens = register("alan@example.com");
        String refresh = tokens.get("refreshToken").asText();

        String body = """
                {"refreshToken":"%s"}
                """.formatted(refresh);
        // First refresh succeeds.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        // Reusing the now-rotated token fails.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointEnforcesRole() throws Exception {
        JsonNode customer = register("bob@example.com");
        String customerAccess = customer.get("accessToken").asText();

        // Customer is forbidden.
        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + customerAccess))
                .andExpect(status().isForbidden());

        // Seeded admin is allowed.
        String adminLogin = """
                {"email":"admin@bank.local","password":"Admin123!"}
                """;
        String adminJson = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(adminLogin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminAccess = mapper.readTree(adminJson).get("accessToken").asText();

        mvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk());
    }
}
