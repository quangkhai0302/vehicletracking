package com.quangkhai.vehicletracking_backend.auth.repository;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"here.routing.enabled=false", "here.traffic.enabled=false", "auth.security-enabled=true"})
@AutoConfigureMockMvc
class DriverPasswordConcurrencyIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @Autowired UserAccountRepository accounts;
    @Autowired DriverRepository drivers;
    @Autowired UserAccountService service;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private static final AtomicInteger IDS = new AtomicInteger();

    private UserAccountEntity account() {
        int id = IDS.incrementAndGet();
        var driver = drivers.saveAndFlush(new DriverEntity("Password fixture " + id,
                String.format("08%08d", id), "PASSWORD-LOCK-" + id));
        return accounts.saveAndFlush(new UserAccountEntity("password.lock." + id,
                encoder.encode("Initial8Pass"), UserRole.DRIVER, driver));
    }

    @Test
    void realLoginRequiresChangeAndInvalidatesAllOldSessionsAfterSuccessfulChange() throws Exception {
        var account = account();
        var first = login(account.getUsername(), "Initial8Pass", true);
        var second = login(account.getUsername(), "Initial8Pass", true);
        mvc.perform(get("/api/v1/auth/me").session(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mvc.perform(get("/api/v1/driver/trips").session(first)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/v1/auth/change-password").session(first).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Initial8Pass\",\"newPassword\":\"Personal8Pass\",\"confirmPassword\":\"Personal8Pass\"}"))
                .andExpect(status().isNoContent());
        assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").session(second)).andExpect(status().isUnauthorized());
        assertThat(second.isInvalid()).isTrue();
        var saved = accounts.findById(account.getId()).orElseThrow();
        assertThat(saved.isPasswordChangeRequired()).isFalse();
        assertThat(encoder.matches("Personal8Pass", saved.getPasswordHash())).isTrue();
        var next = login(account.getUsername(), "Personal8Pass", false);
        mvc.perform(get("/api/v1/driver/trips").session(next)).andExpect(status().isOk());

        service.resetDriverPassword(account.getId(), new DriverPasswordResetRequest("Reset8Pass"));
        mvc.perform(get("/api/v1/auth/me").session(next)).andExpect(status().isUnauthorized());
        var resetLogin = login(account.getUsername(), "Reset8Pass", true);
        mvc.perform(get("/api/v1/driver/trips").session(resetLogin)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    private MockHttpSession login(String username, String password, boolean required) throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(required))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void committedAdminResetCannotBeOverwrittenByAWaitingStaleSession() throws Exception {
        var account = account();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> reset = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.resetDriverPassword(account.getId(), new DriverPasswordResetRequest("Reset8Pass"));
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> change = executor.submit(() -> service.changeDriverPassword(account.getId(),
                        account.getPasswordHash(), new DriverPasswordChangeRequest("Initial8Pass", "Personal8Pass", "Personal8Pass")));
                assertWaitingOnAccountLock(change);
                release.countDown();
                reset.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> change.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(ResponseStatusException.class)
                        .satisfies(error -> assertThat(((ResponseStatusException) error.getCause()).getStatusCode())
                                .isEqualTo(UNAUTHORIZED));
                var saved = accounts.findById(account.getId()).orElseThrow();
                assertThat(encoder.matches("Reset8Pass", saved.getPasswordHash())).isTrue();
                assertThat(saved.isPasswordChangeRequired()).isTrue();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void adminResetAfterSelfChangeRestoresTheRequiredFlag() throws Exception {
        var account = account();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> change = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.changeDriverPassword(account.getId(), account.getPasswordHash(),
                        new DriverPasswordChangeRequest("Initial8Pass", "Personal8Pass", "Personal8Pass"));
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> reset = executor.submit(() -> service.resetDriverPassword(account.getId(),
                        new DriverPasswordResetRequest("Reset8Pass")));
                assertWaitingOnAccountLock(reset);
                release.countDown();
                change.get(10, TimeUnit.SECONDS);
                reset.get(10, TimeUnit.SECONDS);
                var saved = accounts.findById(account.getId()).orElseThrow();
                assertThat(encoder.matches("Reset8Pass", saved.getPasswordHash())).isTrue();
                assertThat(saved.isPasswordChangeRequired()).isTrue();
            } finally {
                release.countDown();
            }
        }
    }

    private void assertWaitingOnAccountLock(Future<?> waiting) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long count = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname=current_database() and wait_event_type='Lock'
                      and query like '%user_accounts%'
                    """, Long.class);
            if (count != null && count > 0) {
                assertThat(waiting.isDone()).isFalse();
                return;
            }
            if (waiting.isDone()) break;
            Thread.sleep(20);
        }
        throw new AssertionError("Concurrent password mutation did not wait on the account row lock");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test release");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
