package com.quangkhai.vehicletracking_backend.auth;

import com.quangkhai.vehicletracking_backend.auth.config.AuthProperties;
import com.quangkhai.vehicletracking_backend.auth.repository.UserAccountRepository;
import com.quangkhai.vehicletracking_backend.auth.service.UserAccountBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserAccountBootstrapTest {
    @Mock UserAccountRepository accounts;
    @Mock PasswordEncoder passwords;

    @Test
    void rejectsConfiguredBootstrapPasswordShorterThanTwelveCharacters() {
        AuthProperties properties = new AuthProperties();
        properties.setBootstrapAdminPassword("too-short");
        UserAccountBootstrap bootstrap = new UserAccountBootstrap(properties, accounts, passwords);

        assertThatThrownBy(bootstrap::createConfiguredAdmin)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters");
        verifyNoInteractions(accounts, passwords);
    }
}
