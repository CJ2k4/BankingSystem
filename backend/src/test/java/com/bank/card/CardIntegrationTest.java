package com.bank.card;

import com.bank.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String token(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123","firstName":"C","lastName":"H"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asText();
    }

    private String openAccount(String token) throws Exception {
        String json = mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    private void deposit(String token, String accountId, String amount) throws Exception {
        mvc.perform(post("/api/v1/accounts/" + accountId + "/deposit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode issueCard(String token, String accountId, String limit) throws Exception {
        String json = mvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + accountId + "\",\"monthlyLimit\":" + limit + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private int pay(String token, String cardId, String amount) throws Exception {
        return mvc.perform(post("/api/v1/cards/" + cardId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchant\":\"Acme\",\"amount\":" + amount + "}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void issueReturnsFullPanOnceAndStoresLast4() throws Exception {
        String token = token("card-issue@example.com");
        String acct = openAccount(token);
        JsonNode res = issueCard(token, acct, "0");

        String pan = res.get("cardNumber").asText();
        assertThat(pan).hasSize(16);
        assertThat(res.get("card").get("last4").asText()).isEqualTo(pan.substring(12));
        assertThat(res.get("card").get("brand").asText()).isEqualTo("VISA");
    }

    @Test
    void cardPaymentDebitsAccount() throws Exception {
        String token = token("card-pay@example.com");
        String acct = openAccount(token);
        deposit(token, acct, "200.00");
        String cardId = issueCard(token, acct, "0").get("card").get("id").asText();

        mvc.perform(post("/api/v1/cards/" + cardId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchant\":\"Coffee Co\",\"amount\":50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountBalanceAfter", is(150.00)));
    }

    @Test
    void monthlyLimitIsEnforced() throws Exception {
        String token = token("card-limit@example.com");
        String acct = openAccount(token);
        deposit(token, acct, "1000.00");
        String cardId = issueCard(token, acct, "100.00").get("card").get("id").asText();

        assertThat(pay(token, cardId, "60.00")).isEqualTo(200);
        assertThat(pay(token, cardId, "60.00")).isEqualTo(422); // 60 + 60 > 100
    }

    @Test
    void frozenCardIsDeclined() throws Exception {
        String token = token("card-frozen@example.com");
        String acct = openAccount(token);
        deposit(token, acct, "100.00");
        String cardId = issueCard(token, acct, "0").get("card").get("id").asText();

        mvc.perform(post("/api/v1/cards/" + cardId + "/freeze")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertThat(pay(token, cardId, "10.00")).isEqualTo(422);
    }

    @Test
    void insufficientFundsIsRejected() throws Exception {
        String token = token("card-funds@example.com");
        String acct = openAccount(token);
        String cardId = issueCard(token, acct, "0").get("card").get("id").asText();
        assertThat(pay(token, cardId, "10.00")).isEqualTo(422);
    }
}
