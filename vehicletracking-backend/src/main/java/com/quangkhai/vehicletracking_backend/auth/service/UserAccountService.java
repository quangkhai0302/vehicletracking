package com.quangkhai.vehicletracking_backend.auth.service;

import com.quangkhai.vehicletracking_backend.auth.dto.AdminRegistrationRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverAccountCreateRequest;
import com.quangkhai.vehicletracking_backend.auth.dto.DriverPasswordResetRequest;
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

import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserAccountRepository accounts;
    private final DriverRepository drivers;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserAccountResponse> findAll() {
        return accounts.findAllByOrderByUsernameAsc().stream().map(UserAccountResponse::from).toList();
    }

    @Transactional
    public UserAccountResponse createDriverAccount(DriverAccountCreateRequest input) {
        String username = normalizeUsername(input.username());
        if (accounts.existsByUsername(username))
            throw new ResponseStatusException(CONFLICT, "Tên đăng nhập đã tồn tại.");
        DriverEntity driver = drivers.findById(input.driverId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài xế."));
        if (!driver.isActive()) throw new ResponseStatusException(CONFLICT, "Tài xế đã ngừng hoạt động.");
        if (accounts.existsByDriverId(driver.getId()))
            throw new ResponseStatusException(CONFLICT, "Tài xế đã có tài khoản đăng nhập.");
        try {
            UserAccountEntity account = accounts.saveAndFlush(new UserAccountEntity(
                    username, passwordEncoder.encode(input.password()), UserRole.DRIVER, driver));
            return UserAccountResponse.from(account);
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
        UserAccountEntity account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài khoản."));
        if (active && account.getDriver() != null && !account.getDriver().isActive())
            throw new ResponseStatusException(CONFLICT, "Hồ sơ tài xế đã ngừng sử dụng.");
        if (active) account.enable(); else account.disable();
        return UserAccountResponse.from(account);
    }

    @Transactional
    public void resetDriverPassword(long id, DriverPasswordResetRequest input) {
        UserAccountEntity account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy tài khoản."));
        if (account.getRole() != UserRole.DRIVER) {
            throw new ResponseStatusException(CONFLICT,
                    "Chỉ có thể đặt lại mật khẩu cho tài khoản tài xế.");
        }
        account.changePassword(passwordEncoder.encode(input.password()));
    }

    public String normalizeUsername(String raw) {
        String username = raw == null ? "" : raw.trim().toLowerCase();
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,99}"))
            throw new ResponseStatusException(BAD_REQUEST, "Tên đăng nhập chỉ gồm chữ thường, số và ._- (3–100 ký tự).");
        return username;
    }
}
