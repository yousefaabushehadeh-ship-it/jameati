import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { post } from '../lib/api';
import { Field } from '../components/ui';

function AuthCard({ title, sub, children }) {
  return (
    <div className="auth">
      <div className="card auth-card">
        <span className="eyebrow">مرحبًا بك في جامعتي</span>
        <h1>{title}</h1>
        {sub && <p className="auth-sub">{sub}</p>}
        {children}
      </div>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [form, setForm] = useState({ email: '', password: '', rememberMe: true });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr('');
    try {
      await login(form.email, form.password, form.rememberMe);
      nav(loc.state?.from || '/me', { replace: true });
    } catch (e2) { setErr(e2.message); }
    setBusy(false);
  };

  return (
    <AuthCard title="تسجيل الدخول" sub="تابع فرصك وخططك من حيث توقفت.">
      <form onSubmit={submit} className="form">
        <Field label="البريد الإلكتروني"><input type="email" required dir="ltr" placeholder="name@example.com" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
        <Field label="كلمة المرور"><input type="password" required dir="ltr" placeholder="••••••••" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></Field>
        <div className="auth-row">
          <label className="check"><input type="checkbox" checked={form.rememberMe} onChange={(e) => setForm({ ...form, rememberMe: e.target.checked })} /> تذكرني</label>
          <Link to="/forgot-password" className="link-sky">نسيت كلمة المرور؟</Link>
        </div>
        {err && <div className="alert alert-error">{err}</div>}
        <button className="btn btn-sky btn-block btn-lg" disabled={busy}>{busy ? 'لحظة…' : 'دخول'}</button>
      </form>
      <p className="auth-alt">ليس لديك حساب؟ <Link to="/register" className="link-sky">أنشئ حسابًا مجانًا</Link></p>
    </AuthCard>
  );
}

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr('');
    try {
      await register(form);
      nav('/me?tab=profile', { replace: true });
    } catch (e2) { setErr(e2.message); }
    setBusy(false);
  };

  return (
    <AuthCard title="إنشاء حساب جديد" sub="ابدأ بتنظيم فرصك وبناء ملفك مجانًا.">
      <form onSubmit={submit} className="form">
        <Field label="الاسم الكامل"><input required maxLength={120} placeholder="مثال: ليان أحمد" value={form.fullName} onChange={set('fullName')} /></Field>
        <Field label="البريد الإلكتروني"><input type="email" required dir="ltr" placeholder="name@example.com" value={form.email} onChange={set('email')} /></Field>
        <Field label="رقم الهاتف"><input type="tel" required placeholder="مثال: 0791234567" value={form.phone} onChange={set('phone')} /></Field>
        <Field label="كلمة المرور" hint="استخدم 8 أحرف على الأقل لكلمة مرور أقوى، ننصح بحرف كبير وحرف صغير ورقم ورمز مثل ! أو @."><input type="password" dir="ltr" required minLength={8} maxLength={72} value={form.password} onChange={set('password')} /></Field>
        {err && <div className="alert alert-error">{err}</div>}
        <button className="btn btn-sky btn-block btn-lg" disabled={busy}>{busy ? 'لحظة…' : 'إنشاء الحساب'}</button>
      </form>
      <p className="auth-alt">لديك حساب؟ <Link to="/login" className="link-sky">سجّل الدخول</Link></p>
    </AuthCard>
  );
}

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try {
      const r = await post('/auth/forgot-password', { email });
      setMsg(r?.message || 'إذا كان البريد مسجل، رح يوصلك رابط إعادة التعيين.');
    } catch (e2) { setErr(e2.message); }
  };
  return (
    <AuthCard title="نسيت كلمة المرور" sub="اكتب بريدك ورح نبعتلك رابط آمن لإعادة التعيين.">
      {msg ? <div className="alert alert-ok">{msg}</div> : (
        <form onSubmit={submit} className="form">
          <Field label="البريد الإلكتروني"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          {err && <div className="alert alert-error">{err}</div>}
          <button className="btn btn-sky btn-block btn-lg">أرسل الرابط</button>
        </form>
      )}
    </AuthCard>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams();
  const [password, setPassword] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try {
      const r = await post('/auth/reset-password', { token: params.get('token'), password });
      setMsg(r?.message || 'تم تغيير كلمة المرور.');
    } catch (e2) { setErr(e2.message); }
  };
  return (
    <AuthCard title="كلمة مرور جديدة">
      {msg ? <div className="alert alert-ok">{msg} <Link to="/login">سجّل دخول</Link></div> : (
        <form onSubmit={submit} className="form">
          <Field label="كلمة المرور الجديدة"><input type="password" required minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
          {err && <div className="alert alert-error">{err}</div>}
          <button className="btn btn-sky btn-block btn-lg">حفظ</button>
        </form>
      )}
    </AuthCard>
  );
}

export function VerifyEmail() {
  const [params] = useSearchParams();
  const [state, setState] = useState({ msg: 'جاري تأكيد بريدك…', ok: null });
  useEffect(() => {
    post('/auth/verify-email', { token: params.get('token') })
      .then((r) => setState({ msg: r?.message || 'تم تأكيد بريدك ✓', ok: true }))
      .catch((e) => setState({ msg: e.message, ok: false }));
  }, [params]);
  return (
    <AuthCard title="تأكيد البريد">
      <div className={`alert ${state.ok === false ? 'alert-error' : 'alert-ok'}`}>{state.msg}</div>
      <Link to="/">الرئيسية</Link>
    </AuthCard>
  );
}
