import React, { createContext, useContext, useState } from 'react';
import { UserDto, AuthResponse } from '../api/types';

interface AuthContextType {
  user: UserDto | null;
  accessToken: string | null;
  login: (auth: AuthResponse) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserDto | null>(() => {
    const saved = localStorage.getItem('knowflow_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [accessToken, setAccessToken] = useState<string | null>(() => {
    return localStorage.getItem('knowflow_access_token');
  });

  const login = (auth: AuthResponse) => {
    setUser(auth.user);
    setAccessToken(auth.accessToken);
    localStorage.setItem('knowflow_user', JSON.stringify(auth.user));
    localStorage.setItem('knowflow_access_token', auth.accessToken);
    localStorage.setItem('knowflow_refresh_token', auth.refreshToken);
  };

  const logout = () => {
    const refreshToken = localStorage.getItem('knowflow_refresh_token');
    if (refreshToken) {
      fetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      }).catch(() => {});
    }
    setUser(null);
    setAccessToken(null);
    localStorage.removeItem('knowflow_user');
    localStorage.removeItem('knowflow_access_token');
    localStorage.removeItem('knowflow_refresh_token');
  };

  return (
    <AuthContext.Provider value={{ user, accessToken, login, logout, isAuthenticated: !!accessToken }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
