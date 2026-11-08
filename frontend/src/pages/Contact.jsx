import { useState } from 'react';
import { post } from '../lib/api';
import { Field } from '../components/ui';

const SUBJECTS = { support: 'دعم فني', content: 'ملاحظة على المحتوى', partnership: 'شراكة', privacy: 'الخصوصية', other: 'غير ذلك' };

export default function Contact() {
  const [form, setForm] = useState({ name: '', email: '', subject: 'support', message: '', consent: false });
  const [res, setRes] = useState(null);
  const [err, setErr] = useState('');
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try { setRes(await post('/public/support/contact', { ...form, pageUrl: '' })); } catch (e2) { setErr(e2.message); }
  };

  return (
    <div className="container section auth">
      <div className="card auth-card">
        <h1>تواصل معنا</h1>
        {res ? (
          <div className="alert alert-ok">وصلتنا رسالتك ✓ رقم المرجع: <strong dir="ltr">{res.referenceCode}</strong></div>
        ) : (
          <form className="form" onSubmit={submit}>
            <Field label="الاسم"><input required maxLength={80} value={form.name} onChange={set('name')} /></Field>
            <Field label="البريد الإلكتروني"><input type="email" required value={form.email} onChange={set('email')} /></Field>
            <Field label="الموضوع">
              <select value={form.subject} onChange={set('subject')}>
                {Object.entries(SUBJECTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </Field>
            <Field label="الرسالة" hint="بين 20 و 2000 حرف"><textarea required minLength={20} maxLength={2000} rows={5} value={form.message} onChange={set('message')} /></Field>
            <label className="check"><input type="checkbox" required checked={form.consent} onChange={set('consent')} /> أوافق على معالجة بياناتي للرد على رسالتي</label>
            {err && <div className="alert alert-error">{err}</div>}
            <button className="btn btn-primary btn-block">إرسال</button>
          </form>
        )}
      </div>
    </div>
  );
}
