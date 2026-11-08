import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, get } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import Icon from '../components/Icon';
import { ErrorBox, Field, Loading } from '../components/ui';
import { date, num } from '../lib/format';

const PLAN_INFO = {
  ANNUAL: { title: 'الباقة السنوية', sub: 'سنة كاملة', perks: ['كامل محتوى المنصة والدورات والأدلة', 'التفاصيل وروابط التقديم والمصادر الرسمية', 'تنبيهات المواعيد النهائية'] },
  QUARTERLY: { title: 'باقة 3 شهور', sub: '3 شهور', perks: ['وصول كامل للمحتوى لمدة 3 شهور'] },
  CONSULTATION: { title: 'باقة الاستشارة', sub: 'متابعة شخصية', perks: ['تحديد المسار ودعم التقديم', 'متابعة حتى الحصول على المنحة أو الوظيفة', 'جلسات استشارية مع فريق جامعتي'] },
};
const CUR = { JOD: 'د.أ', USD: '$' };

export default function Membership() {
  const { user } = useAuth();
  const plan = useFetch(() => get('/public/membership-plan'), [user?.id]);
  const [selected, setSelected] = useState('ANNUAL');

  if (plan.loading) return <Loading />;
  if (plan.error) return <div className="container section"><ErrorBox error={plan.error} onRetry={plan.reload} /></div>;
  const p = plan.data;
  const packages = (p.packages || []).filter((x) => x.code !== 'QUARTERLY');
  const pkg = packages.find((x) => x.code === selected) || packages[0];

  return (
    <>
      <section className="plans-hero">
        <div className="container">
          <span className="pill-ghost pill-gold"><Icon name="crown" size={15} /> عضوية جامعتي+</span>
          <h1>افتح كل الفرص والأدلة بعضوية واحدة</h1>
          <p>بدون عضوية بتقدر تشوف {num(p.freeLimit)} اختيارات مجانية من كل قسم. العضوية بتفتحلك التفاصيل والمصادر وروابط التقديم.</p>
          {p.active && <div className="alert alert-ok">عضويتك فعّالة لغاية {date(p.expiresAt)} 🎉</div>}
          <span className="step-label">الخطوة الأولى</span>
          <div className="plans">
            {packages.map((x) => {
              const info = PLAN_INFO[x.code] || { title: x.code, perks: [] };
              const on = pkg?.code === x.code;
              return (
                <div key={x.code} className={`plan ${on ? 'plan-on' : ''}`}>
                  <span className="muted-xs">{on ? 'الباقة المختارة' : 'باقة'}</span>
                  <h3>{info.title}</h3>
                  <span className="muted-xs">{info.sub}{x.billingPeriodMonths ? ` · ${num(x.billingPeriodMonths)} شهرًا` : ''}</span>
                  <div className="price">{Number(x.price).toFixed(2)} <small>{CUR[x.currency] || x.currency}</small></div>
                  <ul>{info.perks.map((k) => <li key={k}><Icon name="check" size={18} /> {k}</li>)}</ul>
                  <button className={`btn btn-block btn-lg ${on ? 'btn-sky' : 'btn-outline'}`} onClick={() => setSelected(x.code)}>
                    {on ? <><Icon name="cap" size={18} /> الباقة المختارة</> : <><Icon name="calendar" size={18} /> اختيار الباقة</>}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </section>
      {pkg && (
        <section className="container section">
          <div className="checkout-grid">
            <div className="card checkout-summary">
              <span className="muted-xs">الباقة المختارة</span>
              <h3>{PLAN_INFO[pkg.code]?.title}</h3>
              <span className="muted-xs">{PLAN_INFO[pkg.code]?.sub}</span>
              <div className="price price-navy">{Number(pkg.price).toFixed(2)} <small>{CUR[pkg.currency] || pkg.currency}</small></div>
            </div>
            <div className="card checkout">
              <span className="eyebrow">الخطوة الثانية</span>
              <h2>اختر طريقة الدفع</h2>
              <p className="muted">لن يتم إنشاء عملية دفع قبل الضغط على متابعة.</p>
              {user ? <Checkout pkg={pkg} plan={p} onDone={plan.reload} /> : (
                <Link className="btn btn-sky btn-lg" to="/login" state={{ from: '/membership' }}>سجّل الدخول للمتابعة <Icon name="arrow" size={16} /></Link>
              )}
            </div>
          </div>
        </section>
      )}
    </>
  );
}

function Checkout({ pkg, plan, onDone }) {
  const { user } = useAuth();
  const [form, setForm] = useState({ name: user.fullName, email: user.email, paymentReference: '' });
  const [file, setFile] = useState(null);
  const [state, setState] = useState({ busy: false, msg: '', err: '' });

  const submit = async (e) => {
    e.preventDefault();
    setState({ busy: true, msg: '', err: '' });
    const fd = new FormData();
    fd.append('name', form.name);
    fd.append('email', form.email);
    if (form.paymentReference) fd.append('paymentReference', form.paymentReference);
    fd.append('planCode', pkg.code);
    fd.append('receiptImage', file);
    try {
      const r = await api('/me/memberships/checkout', { method: 'POST', body: fd });
      setState({ busy: false, err: '', msg: r.status === 'PAID' ? 'تم تفعيل عضويتك ✓' : 'وصلنا طلبك، رح نراجع الإيصال ونفعّل العضوية قريبًا.' });
      onDone();
    } catch (e2) { setState({ busy: false, msg: '', err: e2.message }); }
  };

  if (state.msg) return <div className="alert alert-ok">{state.msg}</div>;
  return (
    <form className="form" onSubmit={submit}>
      <div className="pay-method on">
        <Icon name="upload" size={22} />
        <div>
          <strong>تحويل CliQ ورفع الإيصال</strong>
          <span className="muted-xs">حوّل المبلغ{plan.cliqAlias && <> للاسم المستعار <code dir="ltr">{plan.cliqAlias}</code></>} وارفع صورة الإيصال.</span>
        </div>
      </div>
      <div className="form-2">
        <Field label="الاسم"><input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="البريد"><input type="email" dir="ltr" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
      </div>
      <Field label="رقم مرجع الحوالة (اختياري)"><input value={form.paymentReference} onChange={(e) => setForm({ ...form, paymentReference: e.target.value })} /></Field>
      <Field label="صورة الإيصال"><input type="file" accept="image/*,application/pdf" required onChange={(e) => setFile(e.target.files[0])} /></Field>
      {state.err && <div className="alert alert-error">{state.err}</div>}
      <button className="btn btn-sky btn-lg" disabled={state.busy}>{state.busy ? 'جاري الإرسال…' : 'متابعة'} <Icon name="arrow" size={16} /></button>
    </form>
  );
}
