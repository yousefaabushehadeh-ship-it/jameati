import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, post } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    try {
      setUser(await api('/auth/session'));
    } catch {
      // نجرب نجدد الجلسة من كوكي الـ refresh
      try {
        const r = await post('/auth/refresh');
        setUser(r?.user || null);
      } catch {
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const login = async (email, password, rememberMe) => {
    const r = await post('/auth/login', { email, password, rememberMe });
    setUser(r.user);
    return r.user;
  };
  const register = async (data) => {
    const r = await post('/auth/register', data);
    setUser(r.user);
    return r.user;
  };
  const logout = async () => {
    try {
      await post('/auth/logout');
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, reload }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
