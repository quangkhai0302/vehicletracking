package com.quangkhai.vehicletracking_backend.auth.controller;

import com.quangkhai.vehicletracking_backend.auth.config.SecurityConfig;
import com.quangkhai.vehicletracking_backend.auth.config.SessionAccountValidationFilter;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.dto.AdminRegistrationRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.UserAccountResponse;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.config.CorsProperties;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, properties = {
        "auth.security-enabled=true", "app.cors.allowed-origins=http://localhost:5173"})
@EnableConfigurationProperties(CorsProperties.class)
@Import({SecurityConfig.class, SessionAccountValidationFilter.class})
class AuthSecurityControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean UserAccountService userAccounts;

    @Test
    void csrfEndpointBootstrapsSpaCookieAndUnauthenticatedSessionIsRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password-12345\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfProtectedLoginCreatesReusableSession() throws Exception {
        UserAccountEntity account = new UserAccountEntity("admin", "encoded", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));
        when(accounts.isActiveForAuthentication(7L, "encoded")).thenReturn(true);
        when(passwords.matches("password-12345", "encoded")).thenReturn(true);

        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = csrf.getResponse().getCookie("XSRF-TOKEN").getValue();
        var login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password-12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    void adminRegistrationIsPublicButStillRequiresCsrf() throws Exception {
        UserAccountResponse created = new UserAccountResponse(9L, "ops", UserRole.ADMIN, true,
                null, null, null);
        when(userAccounts.registerAdmin(any(AdminRegistrationRequest.class))).thenReturn(created);

        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = csrf.getResponse().getCookie("XSRF-TOKEN").getValue();
        mvc.perform(post("/api/v1/auth/register-admin")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops\",\"password\":\"pass1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ops"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        verify(userAccounts).registerAdmin(any(AdminRegistrationRequest.class));
    }

    @Test
    void adminRegistrationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops\",\"password\":\"secure-admin-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRegistrationRejectsPasswordShorterThanEightCharacters() throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = csrf.getResponse().getCookie("XSRF-TOKEN").getValue();

        mvc.perform(post("/api/v1/auth/register-admin")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops\",\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userAccounts);
    }

    @Test
    void driverSessionCannotResetAnotherDriverPassword() throws Exception {
        UserAccountEntity account = new UserAccountEntity(
                "driver", "encoded", UserRole.DRIVER, null);
        ReflectionTestUtils.setField(account, "id", 8L);
        when(accounts.findByUsername("driver")).thenReturn(Optional.of(account));
        when(accounts.isActiveForAuthentication(8L, "encoded")).thenReturn(true);
        when(passwords.matches("password-12345", "encoded")).thenReturn(true);

        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();
        var login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"driver\",\"password\":\"password-12345\"}"))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(post("/api/v1/users/7/reset-password")
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-password\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userAccounts);
    }

    @Test
    void temporaryDriverLoginAndMeExposeFlagAndBusinessRequestsReturnProblemCode() throws Exception {
        LoginSession login = loginDriver(true);

        mvc.perform(get("/api/v1/auth/me").session(login.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mvc.perform(get("/api/v1/driver/trips").session(login.session()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/v1/auth/register-admin").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bypass\",\"password\":\"NewPass1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(userAccounts);
    }

    @Test
    void changePasswordUsesCurrentSessionIdentityAndLogsOutAfterServiceReturns() throws Exception {
        LoginSession login = loginDriver(true);

        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password-12345\",\"newPassword\":\" NewPass1 \","
                                + "\"confirmPassword\":\" NewPass1 \",\"accountId\":999}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<DriverPasswordChangeRequest> input = ArgumentCaptor.forClass(DriverPasswordChangeRequest.class);
        verify(userAccounts).changeDriverPassword(eq(8L), eq("encoded"), input.capture());
        assertThat(input.getValue().newPassword()).isEqualTo(" NewPass1 ");
        assertThat(login.session().isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void regularDriverCanChangePasswordVoluntarily() throws Exception {
        LoginSession login = loginDriver(false);
        mvc.perform(get("/api/v1/auth/me").session(login.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(validChangeBody()))
                .andExpect(status().isNoContent());
        verify(userAccounts).changeDriverPassword(eq(8L), eq("encoded"), any(DriverPasswordChangeRequest.class));
    }

    @Test
    void temporaryDriverChangeStillRequiresCsrfAndCanLogout() throws Exception {
        LoginSession login = loginDriver(true);
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .contentType(MediaType.APPLICATION_JSON).content(validChangeBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue()))
                .andExpect(status().isNoContent());
        assertThat(login.session().isInvalid()).isTrue();
        verifyNoInteractions(userAccounts);
    }

    @Test
    void guestAndAdminCannotUseDriverSelfChangeEndpoint() throws Exception {
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/v1/auth/change-password")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(validChangeBody()))
                .andExpect(status().isUnauthorized());

        UserAccountEntity account = new UserAccountEntity("admin", "encoded", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));
        when(passwords.matches("password-12345", "encoded")).thenReturn(true);
        when(accounts.isActiveForAuthentication(7L, "encoded")).thenReturn(true);
        var login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"password-12345\"}"))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/change-password")
                        .session((MockHttpSession) login.getRequest().getSession(false))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(validChangeBody()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userAccounts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"currentPassword\":\"Current1\",\"newPassword\":\"short\",\"confirmPassword\":\"short\"}",
            "{\"currentPassword\":\"Current1\",\"newPassword\":\"😀😀😀😀\",\"confirmPassword\":\"😀😀😀😀\"}",
            "{\"currentPassword\":\"Current1\",\"newPassword\":\"NewPass1\"}"})
    void invalidChangeBodyIsRejectedBeforeCallingService(String body) throws Exception {
        LoginSession login = loginDriver(true);
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(not("Invalid request content.")))
                .andExpect(jsonPath("$.detail").value(not(containsString("Current1"))))
                .andExpect(jsonPath("$.detail").value(not(containsString("short"))));
        assertThat(login.session().isInvalid()).isFalse();
        verifyNoInteractions(userAccounts);
    }

    @Test
    void eightEmojiPasswordPassesHttpValidationAndLogsOut() throws Exception {
        LoginSession login = loginDriver(true);
        String password = "😀".repeat(8);
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password-12345\",\"newPassword\":\"" + password
                                + "\",\"confirmPassword\":\"" + password + "\"}"))
                .andExpect(status().isNoContent());
        verify(userAccounts).changeDriverPassword(8L, "encoded",
                new DriverPasswordChangeRequest("password-12345", password, password));
        assertThat(login.session().isInvalid()).isTrue();
    }

    @Test
    void utf8PasswordBeyondBcryptLimitIsRejectedAtHttpBoundary() throws Exception {
        LoginSession login = loginDriver(true);
        String tooLong = "ầ".repeat(25);
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Current1\",\"newPassword\":\"" + tooLong
                                + "\",\"confirmPassword\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Mật khẩu mới không được vượt quá 72 byte UTF-8."))
                .andExpect(content().string(not(containsString(tooLong))));
        verifyNoInteractions(userAccounts);
    }

    @Test
    void currentPasswordBeyondBcryptLimitReturnsControlledErrorWithoutExposingInput() throws Exception {
        LoginSession login = loginDriver(true);
        String tooLong = "a".repeat(73);
        mvc.perform(post("/api/v1/auth/change-password").session(login.session())
                        .cookie(login.csrf()).header("X-XSRF-TOKEN", login.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + tooLong
                                + "\",\"newPassword\":\"NewPass1\",\"confirmPassword\":\"NewPass1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Mật khẩu hiện tại không đúng."))
                .andExpect(content().string(not(containsString(tooLong))));
        verifyNoInteractions(userAccounts);
    }

    @Test
    void changedCredentialInvalidatesAnotherSessionBeforeTemporaryPasswordGate() throws Exception {
        LoginSession first = loginDriver(true);
        LoginSession second = loginDriver(true);
        mvc.perform(post("/api/v1/auth/change-password").session(first.session())
                        .cookie(first.csrf()).header("X-XSRF-TOKEN", first.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(validChangeBody()))
                .andExpect(status().isNoContent());
        when(accounts.isActiveForAuthentication(8L, "encoded")).thenReturn(false);

        mvc.perform(get("/api/v1/auth/me").session(second.session())).andExpect(status().isUnauthorized());
        assertThat(second.session().isInvalid()).isTrue();
    }

    private LoginSession loginDriver(boolean required) throws Exception {
        DriverEntity driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 2L);
        UserAccountEntity account = new UserAccountEntity("driver", "encoded", UserRole.DRIVER, driver);
        ReflectionTestUtils.setField(account, "id", 8L);
        if (!required) account.changePassword("encoded");
        when(accounts.findByUsername("driver")).thenReturn(Optional.of(account));
        when(accounts.isActiveForAuthentication(8L, "encoded")).thenReturn(true);
        when(passwords.matches("password-12345", "encoded")).thenReturn(true);
        Cookie csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        var login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"driver\",\"password\":\"password-12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(required)).andReturn();
        return new LoginSession((MockHttpSession) login.getRequest().getSession(false), csrf);
    }

    private String validChangeBody() {
        return "{\"currentPassword\":\"password-12345\",\"newPassword\":\"NewPass1\",\"confirmPassword\":\"NewPass1\"}";
    }

    private record LoginSession(MockHttpSession session, Cookie csrf) {}
}
