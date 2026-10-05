export type UserRole = 'ADMIN' | 'DRIVER';

export interface AuthUser {
  accountId: number;
  username: string;
  role: UserRole;
  active: boolean;
  passwordChangeRequired: boolean;
  driverId: number | null;
  driverName: string | null;
}

export interface LoginInput { username: string; password: string }

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface AdminRegistrationInput {
  username: string;
  password: string;
}

export interface RegisteredAdmin {
  id: number;
  username: string;
  role: 'ADMIN';
  active: boolean;
  driverId: null;
  driverName: null;
  driverLicenseNumber: null;
}
