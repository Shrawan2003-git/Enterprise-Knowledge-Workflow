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
        throw new Error(errData.message || 'Registration failed. Email may already be in use.');
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
    <div className="flex min-h-screen items-center justify-center bg-slate-950 px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-lg space-y-6 rounded-2xl bg-slate-900/90 p-8 shadow-2xl backdrop-blur-xl border border-slate-800">
        <div>
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-xl bg-teal-500/10 text-teal-400 border border-teal-500/30 shadow-lg shadow-teal-500/10">
            <ShieldCheck className="h-8 w-8" />
          </div>
          <h2 className="mt-5 text-center text-3xl font-extrabold tracking-tight text-white">
            Enterprise KnowFlow
          </h2>
          <p className="mt-1 text-center text-sm text-slate-400">
            AI Knowledge & Workflow Copilot
          </p>
        </div>

        {/* Mode Switcher Tabs */}
        <div className="grid grid-cols-2 rounded-xl bg-slate-950/70 p-1 border border-slate-800">
          <button
            type="button"
            onClick={() => { setMode('signin'); setError(null); }}
            className={`py-2 text-sm font-semibold rounded-lg transition-all ${
              mode === 'signin'
                ? 'bg-teal-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setMode('register'); setError(null); }}
            className={`py-2 text-sm font-semibold rounded-lg transition-all ${
              mode === 'register'
                ? 'bg-teal-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Create Role Account
          </button>
        </div>

        {error && (
          <div className="flex items-center gap-2 rounded-lg bg-rose-500/10 p-3.5 text-sm text-rose-400 border border-rose-500/20">
            <AlertCircle className="h-5 w-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {successMsg && (
          <div className="flex items-center gap-2 rounded-lg bg-emerald-500/10 p-3.5 text-sm text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="h-5 w-5 flex-shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {mode === 'signin' ? (
          <form className="space-y-4" onSubmit={handleSignIn}>
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Corporate Email
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/60 py-2.5 pl-10 pr-3 text-white placeholder-slate-500 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Password
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/60 py-2.5 pl-10 pr-10 text-white placeholder-slate-500 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400 hover:text-slate-200 transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="group relative flex w-full justify-center items-center gap-2 rounded-lg bg-teal-600 px-4 py-3 text-sm font-semibold text-white shadow-lg transition-all hover:bg-teal-500 focus:outline-none focus:ring-2 focus:ring-teal-500 disabled:opacity-50"
            >
              {loading ? 'Authenticating...' : 'Sign In to Workspace'}
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
            </button>

            <div className="pt-2 border-t border-slate-800 text-center">
              <span className="text-xs text-slate-400">Quick sign in with pre-seeded role accounts:</span>
              <div className="mt-2 flex flex-wrap justify-center gap-1.5 text-xs">
                <button
                  type="button"
                  onClick={() => setDemoAccount('admin@knowflow.local')}
                  className="px-2.5 py-1 rounded-md bg-slate-800 text-teal-300 hover:bg-slate-700 transition"
                >
                  Admin
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('km@knowflow.local')}
                  className="px-2.5 py-1 rounded-md bg-slate-800 text-teal-300 hover:bg-slate-700 transition"
                >
                  Knowledge Mgr
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('approver@knowflow.local')}
                  className="px-2.5 py-1 rounded-md bg-slate-800 text-teal-300 hover:bg-slate-700 transition"
                >
                  Approver
                </button>
                <button
                  type="button"
                  onClick={() => setDemoAccount('employee@knowflow.local')}
                  className="px-2.5 py-1 rounded-md bg-slate-800 text-teal-300 hover:bg-slate-700 transition"
                >
                  Employee
                </button>
              </div>
            </div>
          </form>
        ) : (
          <form className="space-y-4" onSubmit={handleRegister}>
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Full Name
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <User className="h-5 w-5" />
                </div>
                <input
                  type="text"
                  required
                  value={regName}
                  onChange={(e) => setRegName(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/60 py-2.5 pl-10 pr-3 text-white placeholder-slate-500 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                  placeholder="e.g. Alex Rivera"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Corporate Email
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  type="email"
                  required
                  value={regEmail}
                  onChange={(e) => setRegEmail(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/60 py-2.5 pl-10 pr-3 text-white placeholder-slate-500 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                  placeholder="name@company.com"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Password <span className="text-slate-500 lowercase">(min 8 chars)</span>
              </label>
              <div className="relative mt-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  type={showRegPassword ? 'text' : 'password'}
                  required
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/60 py-2.5 pl-10 pr-10 text-white placeholder-slate-500 focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                  placeholder="At least 8 characters"
                />
                <button
                  type="button"
                  onClick={() => setShowRegPassword(!showRegPassword)}
                  className="absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400 hover:text-slate-200 transition-colors"
                  aria-label={showRegPassword ? 'Hide password' : 'Show password'}
                >
                  {showRegPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-300">
                Assign Enterprise Role
              </label>
              <div className="mt-1">
                <select
                  value={regRole}
                  onChange={(e) => setRegRole(e.target.value as any)}
                  className="block w-full rounded-lg border border-slate-700 bg-slate-950/80 py-2.5 px-3 text-white focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500 text-sm"
                >
                  <option value="EMPLOYEE">Standard Employee (Search, Chat Copilot, View Documents)</option>
                  <option value="KNOWLEDGE_MANAGER">Knowledge Manager (Upload Documents, Manage Ingestion & Chunks)</option>
                  <option value="APPROVER">Lead Approver (Review Workflows & Change Requests)</option>
                  <option value="ADMIN">System Administrator (Full Control, Audits, Users & System Ops)</option>
                </select>
              </div>
              <p className="mt-1.5 text-xs text-slate-400">
                Access permissions and capabilities will automatically adapt to your chosen role.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="group relative flex w-full justify-center items-center gap-2 rounded-lg bg-teal-600 px-4 py-3 text-sm font-semibold text-white shadow-lg transition-all hover:bg-teal-500 focus:outline-none focus:ring-2 focus:ring-teal-500 disabled:opacity-50"
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
