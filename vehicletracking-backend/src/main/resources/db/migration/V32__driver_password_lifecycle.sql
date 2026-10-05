ALTER TABLE vehicle_tracking.user_accounts
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_user_accounts_driver_password_change
        CHECK (role = 'DRIVER' OR password_change_required = FALSE);
