import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, Lock, Mail, User, ArrowRight, AlertCircle, Eye, EyeOff, UserPlus, CheckCircle2 } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const [mode, setMode] = useState<'signin' | 'register'>('signin');

  // Sign in state
  const [email, setEmail] = useState('admin@knowflow.local');
  const [password, setPassword] = useState('Password123!');
  const [showPassword, setShowPassword] = useState(false);

  // Register state
  const [regName, setRegName] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [regRole, setRegRole] = useState<'EMPLOYEE' | 'KNOWLEDGE_MANAGER' | 'APPROVER' | 'ADMIN'>('EMPLOYEE');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim(), password })
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        if (res.status >= 500) {
          throw new Error('Cannot connect to backend server (http://localhost:8080). Please ensure your backend is running in Terminal 1.');
        }
        throw new Error(errData.message || 'Authentication failed. Please check your credentials.');
      }

      const data = await res.json();
      login(data);
    } catch (err: any) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccessMsg(null);

    if (regPassword.length < 8) {
      setError('Password must be at least 8 characters long');
      setLoading(false);
      return;
    }

    try {
      const res = await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: regName.trim(),
          email: regEmail.trim(),
          password: regPassword,
          roles: [regRole]
        })
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        if (res.status >= 500) {
          throw new Error('Cannot connect to backend server (http://localhost:8080). Please ensure your backend is running in Terminal 1.');
        }
        throw new Error(errData.message || (res.status === 409 ? 'Email is already registered. Please sign in or use a different email.' : 'Registration failed. Please check your details.'));
      }

      const data = await res.json();
      setSuccessMsg(`Account created successfully as ${regRole}! Logging you in...`);
      setTimeout(() => {
        login(data);
      }, 700);
    } catch (err: any) {
      setError(err.message || 'Account creation failed');
    } finally {
      setLoading(false);
    }
  };

  const setDemoAccount = (demoEmail: string) => {
    setMode('signin');
    setEmail(demoEmail);
    setPassword('Password123!');
    setError(null);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-scenic-horizon px-4 py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      <div className="w-full max-w-lg space-y-6 neon-radiant-card p-8 md:p-10 shadow-2xl relative z-10">
        <div>
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-600 text-white shadow-lg shadow-blue-500/50">
            <ShieldCheck className="h-9 w-9" />
          </div>
          <h2 className="mt-5 text-center text-3xl font-extrabold tracking-tight text-white">
            Enterprise KnowFlow
          </h2>
          <p className="mt-1 text-center text-sm text-blue-300 font-medium">
            AI Knowledge & Workflow Copilot
          </p>
        </div>

        {/* Mode Switcher Tabs */}
        <div className="grid grid-cols-2 rounded-2xl bg-slate-950/70 p-1.5 border border-slate-800/80">
          <button
            type="button"
            onClick={() => { setMode('signin'); setError(null); }}
            className={`py-2.5 text-xs font-bold rounded-xl transition-all ${
              mode === 'signin'
                ? 'pill-badge-active text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setMode('register'); setError(null); }}
            className={`py-2.5 text-xs font-bold rounded-xl transition-all ${
              mode === 'register'
                ? 'pill-badge-active text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Create Role Account
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-2xl bg-rose-500/10 p-4 text-xs font-medium text-rose-300 border border-rose-500/20 shadow-inner">
            <AlertCircle className="h-5 w-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {successMsg && (
          <div className="flex items-center gap-2 rounded-2xl bg-emerald-500/10 p-4 text-xs font-medium text-emerald-300 border border-emerald-500/20 shadow-inner">
            <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {mode === 'signin' ? (
          <form className="space-y-4" onSubmit={handleSignIn}>
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Corporate Email
              </label>
              <div className="relative mt-1.5">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-500">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="clay-input block w-full py-3.5 pl-11 pr-4 text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Password
              </label>
              <div className="relative mt-1.5">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-500">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="clay-input block w-full py-3.5 pl-11 pr-11 text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-blue-400 transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-electric group relative flex w-full justify-center items-center gap-2 py-3.5 text-sm font-bold text-white shadow-lg disabled:opacity-50 mt-2"
            >
              {loading ? 'Authenticating...' : 'Sign In to Workspace'}
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </button>

            <div className="pt-4 border-t border-slate-700/60 text-center">
              <span className="text-xs text-slate-400">Quick sign in with pre-seeded role accounts:</span>
              <div className="mt-2.5 flex flex-wrap justify-center gap-2 text-xs">
                <button
                  type="button"
                  onClick={() => setDemoAccount('admin@knowflow.local')}
                  className="pill-badge px-3 py-1 font-bold cursor-pointer transition hover:scale-105 hover:text-white"
                >
                  Admin
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('km@knowflow.local')}
                  className="pill-badge px-3 py-1 font-bold cursor-pointer transition hover:scale-105 hover:text-white"
                >
                  Knowledge Mgr
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('approver@knowflow.local')}
                  className="pill-badge px-3 py-1 font-bold cursor-pointer transition hover:scale-105 hover:text-white"
                >
                  Approver
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('employee@knowflow.local')}
                  className="pill-badge px-3 py-1 font-bold cursor-pointer transition hover:scale-105 hover:text-white"
                >
                  Employee
                </button>
              </div>
            </div>
          </form>
        ) : (
          <form className="space-y-4" onSubmit={handleRegister}>
            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Full Name
              </label>
              <div className="relative mt-1.5">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-500">
                  <User className="h-5 w-5" />
                </div>
                <input
                  type="text"
                  required
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  className="clay-input block w-full py-3.5 pl-11 pr-4 text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="e.g. Alex Rivera"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Corporate Email
              </label>
              <div className="relative mt-1.5">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-500">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  type="email"
                  required
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  className="clay-input block w-full py-3.5 pl-11 pr-4 text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Password <span className="text-slate-500 lowercase">(min 8 chars)</span>
              </label>
              <div className="relative mt-1.5">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4 text-slate-500">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  type={showRegPassword ? 'text' : 'password'}
                  required
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  className="clay-input block w-full py-3.5 pl-11 pr-11 text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="At least 8 characters"
                />
                <button
                  type="button"
                  onClick={() => setShowRegPassword(!showRegPassword)}
                  className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-slate-400 hover:text-blue-400 transition-colors"
                  aria-label={showRegPassword ? 'Hide password' : 'Show password'}
                >
                  {showRegPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div>
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300 ml-1">
                Assign Enterprise Role
              </label>
              <div className="mt-1.5">
                <select
                  value={regRole}
                  onChange={(e) => setRegRole(e.target.value as any)}
                  className="clay-input block w-full py-3.5 px-4 text-sm text-white focus:outline-none cursor-pointer bg-[#0d152a]"
                >
                  <option value="EMPLOYEE" className="bg-slate-900">Standard Employee (Search, Chat Copilot, View Documents)</option>
                  <option value="KNOWLEDGE_MANAGER" className="bg-slate-900">Knowledge Manager (Upload Documents, Manage Ingestion & Chunks)</option>
                  <option value="APPROVER" className="bg-slate-900">Lead Approver (Review Workflows & Change Requests)</option>
                  <option value="ADMIN" className="bg-slate-900">System Administrator (Full Control, Audits, Users & System Ops)</option>
                </select>
              </div>
              <p className="mt-1.5 text-xs text-slate-400">
                Access permissions and capabilities will automatically adapt to your chosen role.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-electric group relative flex w-full justify-center items-center gap-2 py-3.5 text-sm font-bold text-white shadow-lg disabled:opacity-50 mt-2"
            >
              {loading ? 'Registering Account...' : 'Create Role Account & Sign In'}
              <UserPlus className="h-4 w-4" />
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
