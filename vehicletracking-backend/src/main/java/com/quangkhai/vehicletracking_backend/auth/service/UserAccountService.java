package com.quangkhai.vehicletracking_backend.auth.service;

import com.quangkhai.vehicletracking_backend.auth.dto.AdminRegistrationRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreateRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreatedResponse;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordChangeRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.UserAccountResponse;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.driver.entity.DriverEntity;
import com.quangkhai.vehicletracking_backend.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private static final int TEMPORARY_PASSWORD_LENGTH = 8;
    private static final char[] TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final UserAccountRepository accounts;
    private final DriverRepository drivers;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom temporaryPasswordRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<UserAccountResponse> findAll() {
        return accounts.findAllByOrderByUsernameAsc().stream().map(UserAccountResponse::from).toList();
    }

    @Transactional
    public DriverAccountCreatedResponse createDriverAccount(DriverAccountCreateRequest input) {
        DriverEntity driver = drivers.findById(input.driverId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
        if (!driver.isActive()) throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng hoạt động.");
        if (accounts.existsByDriverId(driver.getId()))
            throw new ResponseStatusException(CONFLICT, "Tài xế đã có tài khoản đăng nhập.");
        String username = nextDriverUsername(driver.getFullName());
        String temporaryPassword = generateTemporaryPassword();
        try {
            UserAccountEntity account = accounts.saveAndFlush(new UserAccountEntity(
                    username, passwordEncoder.encode(temporaryPassword), UserRole.DRIVER, driver));
            return DriverAccountCreatedResponse.from(account, temporaryPassword);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Tài khoản hoặc tài xế đã được gán.", ex);
        }
    }

    /** Creates an administrator without accepting a caller-supplied role. */
    @Transactional
    public UserAccountResponse registerAdmin(AdminRegistrationRequest input) {
        String username = normalizeUsername(input.username());
        if (accounts.existsByUsername(username)) {
            throw new ResponseStatusException(CONFLICT, "Tên đăng nhập đã tồn tại.");
        }
        try {
            UserAccountEntity account = accounts.saveAndFlush(new UserAccountEntity(
                    username, passwordEncoder.encode(input.password()), UserRole.ADMIN, null));
            return UserAccountResponse.from(account);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Tên đăng nhập đã tồn tại.", ex);
        }
    }

    @Transactional
    public UserAccountResponse setActive(long id, boolean active) {
        UserAccountEntity account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài khoản."));
        if (active && account.getDriver() != null && !account.getDriver().isActive())
            throw new ResponseStatusException(CONFLICT, "Hồ sơ tài xế đã ngừng sử dụng.");
        if (active) account.enable(); else account.disable();
        return UserAccountResponse.from(account);
    }

    @Transactional
    public void resetDriverPassword(long id, DriverPasswordResetRequest input) {
        UserAccountEntity account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài khoản."));
        if (account.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(CONFLICT,
                    "Chỉ có thể đặt lại mật khẩu cho tài khoản tài xế.");
        }
        if (input.password() == null || input.password().isBlank()
                || !input.isPasswordLengthValid()) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu phải có từ 8 đến 100 ký tự.");
        }
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        account.resetPassword(passwordEncoder.encode(input.password()));
    }

    @Transactional
    public void changeDriverPassword(long id, String sessionPasswordHash, DriverPasswordChangeRequest input) {
        UserAccountEntity account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Phiên đăng nhập không còn hợp lệ."));
        if (account.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(FORBIDDEN, "Chỉ tài xế được sử dụng chức năng đổi mật khẩu này.");
        }
        if (!account.isActive() || account.getDriver() == null || !account.getDriver().isActive()) {
            throw new ResponseStatusException(FORBIDDEN, "Tài khoản hoặc hồ sơ tài xế đã ngừng hoạt động.");
        }
        if (!account.getPasswordHash().equals(sessionPasswordHash)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Mật khẩu đã thay đổi. Vui lòng đăng nhập lại.");
        }
        validatePasswordChange(input);
        if (!passwordEncoder.matches(input.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }
        if (passwordEncoder.matches(input.newPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        account.changePassword(passwordEncoder.encode(input.newPassword()));
    }

    private void validatePasswordChange(DriverPasswordChangeRequest input) {
        if (input == null || input.currentPassword() == null || input.currentPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nhập mật khẩu hiện tại.");
        }
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }
        if (input.newPassword() == null || input.newPassword().isBlank()
                || !input.isNewPasswordLengthValid()) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu mới phải có từ 8 đến 100 ký tự.");
        }
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(BAD_REQUEST, "Mật khẩu mới không được vượt quá 72 byte UTF-8.");
        }
        if (!input.newPassword().equals(input.confirmPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Xác nhận mật khẩu mới không khớp.");
        }
    }

    String nextDriverUsername(String fullName) {
        String base = driverUsernameBase(fullName);
        int suffix = 0;
        while (true) {
            String suffixText = suffix == 0 ? "" : Integer.toString(suffix);
            int baseLength = Math.min(base.length(), 100 - suffixText.length());
            String candidate = base.substring(0, baseLength) + suffixText;
            if (!accounts.existsByUsername(candidate)) return candidate;
            suffix++;
        }
    }

    String driverUsernameBase(String fullName) {
        String ascii = Normalizer.normalize(fullName == null ? "" : fullName, Normalizer.Form.NFD)
                .replace("Đ", "D")
                .replace("đ", "d")
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (ascii.isEmpty()) return "taixe";

        String[] words = ascii.split("\\s+");
        StringBuilder username = new StringBuilder(words[words.length - 1]);
        for (int index = 0; index < words.length - 1; index++) {
            if (!words[index].isEmpty()) username.append(words[index].charAt(0));
        }
        return username.toString();
    }

    String generateTemporaryPassword() {
        char[] password = new char[TEMPORARY_PASSWORD_LENGTH];
        for (int index = 0; index < password.length; index++) {
            password[index] = TEMPORARY_PASSWORD_ALPHABET[
                    temporaryPasswordRandom.nextInt(TEMPORARY_PASSWORD_ALPHABET.length)];
        }
        return new String(password);
    }

    public String normalizeUsername(String raw) {
        String username = raw == null ? "" : raw.trim().toLowerCase();
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,99}"))
            throw new ResponseStatusException(BAD_REQUEST, "Tên đăng nhập chỉ gồm chữ thường, số và ._- (3–100 ký tự).");
        return username;
    }
}
