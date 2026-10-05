package com.quangkhai.vehicletracking_backend.auth;

import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountService;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DriverPasswordChangeServiceTest {
    private static final String CURRENT = " Current1 ";
    @Mock UserAccountRepository accounts;
    @Mock DriverRepository drivers;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserAccountEntity account;
    private UserAccountService service;

    @BeforeEach
    void setup() {
        account = new UserAccountEntity("driver", encoder.encode(CURRENT), UserRole.DRIVER,
                new DriverEntity("Nguyễn Văn A", "0901234567", "B2-123"));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        service = new UserAccountService(accounts, drivers, encoder);
    }

    @Test
    void changesHashAndClearsTemporaryFlagWithoutTrimmingPasswords() {
        String oldHash = account.getPasswordHash();
        service.changeDriverPassword(7L, oldHash, new DriverPasswordChangeRequest(CURRENT, " NewPass1 ", " NewPass1 "));

        assertThat(account.isPasswordChangeRequired()).isFalse();
        assertThat(encoder.matches(" NewPass1 ", account.getPasswordHash())).isTrue();
        assertThat(encoder.matches("NewPass1", account.getPasswordHash())).isFalse();
        assertThat(encoder.matches(CURRENT, account.getPasswordHash())).isFalse();
        verify(accounts).findByIdForUpdate(7L);
    }

    @Test
    void allowsVoluntaryChangeAfterInitialPasswordWasAlreadyChanged() {
        account.changePassword(account.getPasswordHash());
        service.changeDriverPassword(7L, account.getPasswordHash(),
                new DriverPasswordChangeRequest(CURRENT, "NextPass1", "NextPass1"));
        assertThat(encoder.matches("NextPass1", account.getPasswordHash())).isTrue();
        assertThat(account.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void acceptsExactlySeventyTwoUtf8Bytes() {
        String newPassword = "ầ".repeat(24);
        service.changeDriverPassword(7L, account.getPasswordHash(),
                new DriverPasswordChangeRequest(CURRENT, newPassword, newPassword));
        assertThat(encoder.matches(newPassword, account.getPasswordHash())).isTrue();
    }

    @Test
    void eightSupplementaryUnicodeCharactersMeetTheMinimumAndClearRequiredFlag() {
        String newPassword = "😀".repeat(8);
        service.changeDriverPassword(7L, account.getPasswordHash(),
                new DriverPasswordChangeRequest(CURRENT, newPassword, newPassword));
        assertThat(encoder.matches(newPassword, account.getPasswordHash())).isTrue();
        assertThat(account.isPasswordChangeRequired()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidInputWithoutChangingHashOrFlag(DriverPasswordChangeRequest input) {
        String oldHash = account.getPasswordHash();
        assertThatThrownBy(() -> service.changeDriverPassword(7L, oldHash, input))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
        assertThat(account.getPasswordHash()).isEqualTo(oldHash);
        assertThat(account.isPasswordChangeRequired()).isTrue();
    }

    static Stream<DriverPasswordChangeRequest> invalidInputs() {
        return Stream.of(
                new DriverPasswordChangeRequest(null, "NewPass1", "NewPass1"),
                new DriverPasswordChangeRequest("wrong", "NewPass1", "NewPass1"),
                new DriverPasswordChangeRequest("a".repeat(73), "NewPass1", "NewPass1"),
                new DriverPasswordChangeRequest("ầ".repeat(25), "NewPass1", "NewPass1"),
                new DriverPasswordChangeRequest(CURRENT, null, null),
                new DriverPasswordChangeRequest(CURRENT, "short12", "short12"),
                new DriverPasswordChangeRequest(CURRENT, "😀".repeat(4), "😀".repeat(4)),
                new DriverPasswordChangeRequest(CURRENT, "a".repeat(101), "a".repeat(101)),
                new DriverPasswordChangeRequest(CURRENT, "a".repeat(73), "a".repeat(73)),
                new DriverPasswordChangeRequest(CURRENT, "ầ".repeat(25), "ầ".repeat(25)),
                new DriverPasswordChangeRequest(CURRENT, "        ", "        "),
                new DriverPasswordChangeRequest(CURRENT, "NewPass1", "Different1"),
                new DriverPasswordChangeRequest(CURRENT, "NewPass1", null),
                new DriverPasswordChangeRequest(CURRENT, CURRENT, CURRENT));
    }

    @Test
    void rejectsCredentialChangedAfterSessionValidationWhileHoldingAccountLock() {
        String oldHash = account.getPasswordHash();
        account.resetPassword(encoder.encode("ResetPass1"));
        String resetHash = account.getPasswordHash();
        assertThatThrownBy(() -> service.changeDriverPassword(7L, oldHash,
                new DriverPasswordChangeRequest(CURRENT, "NewPass1", "NewPass1")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        assertThat(account.getPasswordHash()).isEqualTo(resetHash);
        assertThat(account.isPasswordChangeRequired()).isTrue();
    }

    @Test
    void rejectsDisabledAccountAndDriver() {
        account.disable();
        assertForbidden();
        account.enable();
        account.getDriver().deactivate();
        assertForbidden();
    }

    @Test
    void rejectsAdminWithoutChangingPassword() {
        account = new UserAccountEntity("admin", encoder.encode(CURRENT), UserRole.ADMIN, null);
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        assertForbidden();
        assertThat(encoder.matches(CURRENT, account.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsMissingAccountAsStaleSession() {
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeDriverPassword(7L, "old-hash",
                new DriverPasswordChangeRequest(CURRENT, "NewPass1", "NewPass1")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> service.changeDriverPassword(7L, account.getPasswordHash(),
                new DriverPasswordChangeRequest(CURRENT, "NewPass1", "NewPass1")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
    }
}
