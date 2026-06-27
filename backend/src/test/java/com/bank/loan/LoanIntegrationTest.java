package com.bank.loan;

import com.bank.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoanIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    private String token(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Secret123","firstName":"L","lastName":"N"}
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

    private String meId(String token) throws Exception {
        String json = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("userId").asText();
    }

    private void verifyKyc(String adminToken, String userId) throws Exception {
        mvc.perform(post("/api/v1/admin/users/" + userId + "/kyc")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VERIFIED\"}"))
                .andExpect(status().isOk());
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

    private JsonNode apply(String token, String accountId, String principal, int term) throws Exception {
        String json = mvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + accountId + "\",\"principal\":" + principal
                                + ",\"termMonths\":" + term + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private double balance(String token, String accountId) throws Exception {
        String json = mvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("balance").asDouble();
    }

    @Test
    void applyRequiresVerifiedKyc() throws Exception {
        String token = token("loan-nokyc@example.com");
        String acct = openAccount(token);
        mvc.perform(post("/api/v1/loans")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + acct + "\",\"principal\":300,\"termMonths\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullLoanLifecycle() throws Exception {
        String token = token("loan-life@example.com");
        String admin = adminToken();
        verifyKyc(admin, meId(token));
        String acct = openAccount(token);
        deposit(token, acct, "100.00"); // buffer to cover interest

        String loanId = apply(token, acct, "300.00", 3).get("id").asText();

        // Customer cannot approve.
        mvc.perform(post("/api/v1/admin/loans/" + loanId + "/approve")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Admin approves -> schedule generated and principal disbursed.
        mvc.perform(post("/api/v1/admin/loans/" + loanId + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loan.status").value("ACTIVE"))
                .andExpect(jsonPath("$.schedule.length()").value(3));
        assertThat(balance(token, acct)).isEqualTo(400.00); // 100 buffer + 300 principal

        // Repay all three installments -> PAID_OFF.
        String last = "";
        for (int i = 0; i < 3; i++) {
            last = mvc.perform(post("/api/v1/loans/" + loanId + "/repay")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
        assertThat(mapper.readTree(last).get("loan").get("status").asText()).isEqualTo("PAID_OFF");

        // The whole ledger still balances to zero.
        BigDecimal sum = jdbc.queryForObject(
                "select coalesce(sum(case when direction='CREDIT' then amount else -amount end),0) "
                        + "from ledger_entries", BigDecimal.class);
        assertThat(sum.signum()).isZero();
    }

    @Test
    void repaymentWithoutFundsIsRejected() throws Exception {
        String token = token("loan-broke@example.com");
        String admin = adminToken();
        verifyKyc(admin, meId(token));
        String acct = openAccount(token);
        String loanId = apply(token, acct, "300.00", 3).get("id").asText();

        mvc.perform(post("/api/v1/admin/loans/" + loanId + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        // Drain the disbursed principal, then a repayment must be declined.
        mvc.perform(post("/api/v1/accounts/" + acct + "/withdraw")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":300.00}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/loans/" + loanId + "/repay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }
}
