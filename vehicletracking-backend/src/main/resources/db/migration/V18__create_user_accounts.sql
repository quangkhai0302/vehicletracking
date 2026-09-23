CREATE TABLE vehicle_tracking.user_accounts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    driver_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_user_accounts_username UNIQUE (username),
    CONSTRAINT uk_user_accounts_driver UNIQUE (driver_id),
    CONSTRAINT fk_user_accounts_driver FOREIGN KEY (driver_id)
        REFERENCES vehicle_tracking.drivers(id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('ADMIN', 'DRIVER')),
    CONSTRAINT ck_user_accounts_role_driver CHECK (
        (role = 'ADMIN' AND driver_id IS NULL)
        OR (role = 'DRIVER' AND driver_id IS NOT NULL)
    )
);

CREATE INDEX idx_user_accounts_role_active ON vehicle_tracking.user_accounts(role, active);
