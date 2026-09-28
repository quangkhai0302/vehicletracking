package com.quangkhai.vehicletracking_backend.auth;

import com.quangkhai.vehicletracking_backend.auth.dto.AdminRegistrationRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreateRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreatedResponse;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.UserAccountResponse;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {
    @Mock UserAccountRepository accounts;
    @Mock DriverRepository drivers;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void normalizeUsernameLowercasesAndRejectsUnsafeValues() {
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        assertThat(service.normalizeUsername("  Nguyen.Van.A ")).isEqualTo("nguyen.van.a");
        assertThatThrownBy(() -> service.normalizeUsername("bad space"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void cannotReEnableAccountForInactiveDriver() {
        DriverEntity driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        driver.deactivate();
        UserAccountEntity account = new UserAccountEntity("driver.a", "hash", UserRole.DRIVER, driver);
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        assertThatThrownBy(() -> service.setActive(7L, true))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verify(accounts, never()).save(any());
    }

    @Test
    void resetDriverPasswordEncodesPasswordAndKeepsAccessState() {
        DriverEntity driver = new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123");
        UserAccountEntity account = new UserAccountEntity("driver.a", "old-hash", UserRole.DRIVER, driver);
        account.disable();
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        service.resetDriverPassword(7L, new DriverPasswordResetRequest("new-password"));

        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(account.getUpdatedAt()).isNotNull();
        assertThat(account.isActive()).isFalse();
        verify(passwordEncoder).encode("new-password");
    }

    @Test
    void resetDriverPasswordRejectsAdminAccount() {
        UserAccountEntity account = new UserAccountEntity(
                "admin", "old-hash", UserRole.ADMIN, null);
        ReflectionTestUtils.setField(account, "id", 8L);
        when(accounts.findById(8L)).thenReturn(Optional.of(account));
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        assertThatThrownBy(() -> service.resetDriverPassword(
                8L, new DriverPasswordResetRequest("new-password")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> {
                            assertThat(error.getStatusCode().value()).isEqualTo(409);
                            assertThat(error.getReason()).isEqualTo(
                                    "Chỉ có thể đặt lại mật khẩu cho tài khoản tài xế.");
                        });
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void registerAdminCreatesAdminWithEncodedPassword() {
        when(accounts.existsByUsername("ops")).thenReturn(false);
        when(passwordEncoder.encode("secure-admin-password")).thenReturn("bcrypt-hash");
        when(accounts.saveAndFlush(any(UserAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        UserAccountResponse response = service.registerAdmin(new AdminRegistrationRequest(
                " OPS ", "secure-admin-password"));

        assertThat(response.username()).isEqualTo("ops");
        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.driverId()).isNull();
        verify(passwordEncoder).encode("secure-admin-password");
        verify(accounts).saveAndFlush(argThat(account -> account.getRole() == UserRole.ADMIN
                && account.getDriver() == null && account.getPasswordHash().equals("bcrypt-hash")));
    }

    @Test
    void registerAdminRejectsDuplicateUsername() {
        when(accounts.existsByUsername("ops")).thenReturn(true);
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        assertThatThrownBy(() -> service.registerAdmin(new AdminRegistrationRequest(
                "ops", "secure-admin-password")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void createDriverAccountGeneratesVietnameseUsernameAndTemporaryPassword() {
        DriverEntity driver = new DriverEntity("Nguyễn Quang Khải", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 7L);
        when(drivers.findById(7L)).thenReturn(Optional.of(driver));
        when(accounts.existsByDriverId(7L)).thenReturn(false);
        when(accounts.existsByUsername("khainq")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("temporary-hash");
        when(accounts.saveAndFlush(any(UserAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        DriverAccountCreatedResponse response = service.createDriverAccount(
                new DriverAccountCreateRequest(7L));

        assertThat(response.username()).isEqualTo("khainq");
        assertThat(response.temporaryPassword()).hasSize(8)
                .matches("[A-HJ-NP-Za-km-z2-9]{8}");
        verify(passwordEncoder).encode(response.temporaryPassword());
        verify(accounts).saveAndFlush(argThat(saved ->
                saved.getUsername().equals("khainq")
                        && saved.getPasswordHash().equals("temporary-hash")
                        && saved.getDriver() == driver));
    }

    @Test
    void createDriverAccountAppendsTheFirstAvailableNumericSuffix() {
        DriverEntity driver = new DriverEntity("Nguyễn Quang Khải", "0901234567", "B2-123");
        ReflectionTestUtils.setField(driver, "id", 7L);
        when(drivers.findById(7L)).thenReturn(Optional.of(driver));
        when(accounts.existsByUsername("khainq")).thenReturn(true);
        when(accounts.existsByUsername("khainq1")).thenReturn(true);
        when(accounts.existsByUsername("khainq2")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("temporary-hash");
        when(accounts.saveAndFlush(any(UserAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        DriverAccountCreatedResponse response = service.createDriverAccount(
                new DriverAccountCreateRequest(7L));

        assertThat(response.username()).isEqualTo("khainq2");
    }

    @Test
    void driverUsernameBaseRemovesVietnameseDiacriticsAndUsesFamilyMiddleInitials() {
        UserAccountService service = new UserAccountService(accounts, drivers, passwordEncoder);

        assertThat(ReflectionTestUtils.<String>invokeMethod(
                service, "driverUsernameBase", "Đỗ Đức Duy")).isEqualTo("duydd");
        assertThat(ReflectionTestUtils.<String>invokeMethod(
                service, "driverUsernameBase", "Trần Thị Ánh")).isEqualTo("anhtt");
    }
}
