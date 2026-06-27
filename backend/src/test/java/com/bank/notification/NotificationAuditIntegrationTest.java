package com.bank.notification;

import com.bank.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationAuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String token(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123","firstName":"No","lastName":"Tify"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asText();
    }

    private String adminToken() throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@bank.local\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asText();
    }

    private JsonNode notifications(String token) throws Exception {
        String json = mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private long unread(String token) throws Exception {
        String json = mvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("count").asLong();
    }

    private boolean hasAction(JsonNode arr, String action) {
        for (JsonNode n : arr) {
            if (action.equals(n.get("type").asText())) return true;
        }
        return false;
    }

    private String openAccount(String token) throws Exception {
        return mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registrationCreatesWelcomeNotificationAndAudit() throws Exception {
        String token = token("notify-welcome@example.com");

        // Async: the Kafka consumer creates the notification shortly after register.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .untilAsserted(() -> assertThat(hasAction(notifications(token), "USER_REGISTERED")).isTrue());

        assertThat(unread(token)).isGreaterThan(0);
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(unread(token)).isZero();

        // The audit consumer recorded the same action (visible to admin).
        String admin = adminToken();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    String json = mvc.perform(get("/api/v1/admin/audit?size=200")
                                    .header("Authorization", "Bearer " + admin))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    JsonNode arr = mapper.readTree(json);
                    boolean found = false;
                    for (JsonNode a : arr) {
                        if ("USER_REGISTERED".equals(a.get("action").asText())) found = true;
                    }
                    assertThat(found).isTrue();
                });
    }

    @Test
    void transferNotifiesSenderAndRecipient() throws Exception {
        String sender = token("notify-sender@example.com");
        String recipient = token("notify-recipient@example.com");

        String senderAcct = mapper.readTree(openAccount(sender)).get("id").asText();
        JsonNode recipientAcctJson = mapper.readTree(openAccount(recipient));
        String recipientNumber = recipientAcctJson.get("accountNumber").asText();

        mvc.perform(post("/api/v1/accounts/" + senderAcct + "/deposit")
                        .header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100.00}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + sender)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + senderAcct + "\",\"destinationAccountNumber\":\""
                                + recipientNumber + "\",\"amount\":25.00}"))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .ignoreExceptions()
                .untilAsserted(() -> {
                    assertThat(hasAction(notifications(sender), "TRANSFER_SENT")).isTrue();
                    assertThat(hasAction(notifications(recipient), "TRANSFER_RECEIVED")).isTrue();
                });
    }
}
