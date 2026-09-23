package com.quangkhai.vehicletracking_backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private boolean securityEnabled = true;
    private String bootstrapAdminUsername = "admin";
    private String bootstrapAdminPassword = "";

    public boolean isSecurityEnabled() { return securityEnabled; }
    public void setSecurityEnabled(boolean value) { securityEnabled = value; }
    public String getBootstrapAdminUsername() { return bootstrapAdminUsername; }
    public void setBootstrapAdminUsername(String value) { bootstrapAdminUsername = value; }
    public String getBootstrapAdminPassword() { return bootstrapAdminPassword; }
    public void setBootstrapAdminPassword(String value) { bootstrapAdminPassword = value; }
}
