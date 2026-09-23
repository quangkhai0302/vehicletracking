package com.quangkhai.vehicletracking_backend.auth.service;

import com.quangkhai.vehicletracking_backend.auth.config.AuthProperties;
import com.quangkhai.vehicletracking_backend.auth.entity.UserAccountEntity;
import com.quangkhai.vehicletracking_backend.auth.entity.UserRole;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth", name = "security-enabled", havingValue = "true", matchIfMissing = true)
public class UserAccountBootstrap {
    static final int MINIMUM_BOOTSTRAP_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(UserAccountBootstrap.class);
    private final AuthProperties properties;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createConfiguredAdmin() {
        String password = properties.getBootstrapAdminPassword();
        if (password == null || password.isBlank()) {
            log.warn("No bootstrap admin password configured; no admin account was created.");
            return;
        }
        if (password.length() < MINIMUM_BOOTSTRAP_PASSWORD_LENGTH) {
            throw new IllegalStateException("AUTH_BOOTSTRAP_ADMIN_PASSWORD must contain at least "
                    + MINIMUM_BOOTSTRAP_PASSWORD_LENGTH + " characters.");
        }
        String username = properties.getBootstrapAdminUsername().trim().toLowerCase();
        if (accounts.existsByUsername(username)) return;
        accounts.save(new UserAccountEntity(username, passwordEncoder.encode(password), UserRole.ADMIN, null));
        log.info("Bootstrap admin account '{}' created.", username);
    }
}
