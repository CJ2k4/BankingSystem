package com.bank.account;

import com.bank.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountLedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    private String registerToken(String email) throws Exception {
        String body = """
                {"email":"%s","password":"Secret123","firstName":"Test","lastName":"User"}
                """.formatted(email);
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asText();
    }

    private String createAccount(String token) throws Exception {
        String json = mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CHECKING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance", org.hamcrest.Matchers.is(0)))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asText();
    }

    private void amount(String token, String accountId, String op, String amount) throws Exception {
        mvc.perform(post("/api/v1/accounts/" + accountId + "/" + op)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void depositAndWithdrawAdjustBalance() throws Exception {
        String token = registerToken("acct1@example.com");
        String accountId = createAccount(token);

        amount(token, accountId, "deposit", "100.00");
        amount(token, accountId, "withdraw", "30.00");

        mvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", org.hamcrest.Matchers.is(70.00)));

        // History: newest first, with running balanceAfter.
        String txns = mvc.perform(get("/api/v1/accounts/" + accountId + "/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = mapper.readTree(txns);
        assertThat(arr).hasSize(2);
        assertThat(arr.get(0).get("type").asText()).isEqualTo("WITHDRAWAL");
        assertThat(arr.get(0).get("balanceAfter").asDouble()).isEqualTo(70.00);
        assertThat(arr.get(1).get("type").asText()).isEqualTo("DEPOSIT");
        assertThat(arr.get(1).get("balanceAfter").asDouble()).isEqualTo(100.00);
    }

    @Test
    void withdrawBeyondBalanceIsRejected() throws Exception {
        String token = registerToken("acct2@example.com");
        String accountId = createAccount(token);

        mvc.perform(post("/api/v1/accounts/" + accountId + "/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void everyTransactionBalancesToZero() throws Exception {
        String token = registerToken("acct3@example.com");
        String accountId = createAccount(token);
        amount(token, accountId, "deposit", "250.50");
        amount(token, accountId, "withdraw", "100.25");

        // The core double-entry invariant: each transaction's signed entries sum to zero.
        List<java.math.BigDecimal> sums = jdbc.query(
                "select sum(case when direction = 'CREDIT' then amount else -amount end) as s "
                        + "from ledger_entries group by transaction_id",
                (rs, n) -> rs.getBigDecimal("s"));
        assertThat(sums).isNotEmpty();
        assertThat(sums).allSatisfy(s -> assertThat(s.signum()).isZero());
    }

    @Test
    void cannotAccessAnotherUsersAccount() throws Exception {
        String alice = registerToken("alice.acct@example.com");
        String accountId = createAccount(alice);
        String bob = registerToken("bob.acct@example.com");

        mvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/accounts/" + accountId + "/deposit")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isNotFound());
    }
}
