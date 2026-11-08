import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { del, get, patch, post, put, pageOf } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import { Empty, ErrorBox, Field, Loading, PageHero } from '../components/ui';
import { APP_STATUS, FUNDING, date, deadlineText } from '../lib/format';

const TABS = [
  ['overview', 'نظرة عامة'],
  ['applications', 'طلباتي'],
  ['favorites', 'المفضلة'],
  ['notifications', 'الإشعارات'],
  ['profile', 'ملفي الشخصي'],
];

export default function Me() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') || 'overview';

  return (
    <>
    <PageHero dark title={`أهلًا ${user.fullName} 👋`} sub="تابع فرصك المحفوظة وطلباتك ومواعيدك من مكان واحد." />
    <div className="container section">
      <div className="tabs">
        {TABS.map(([k, label]) => (
          <button key={k} className={`tab ${tab === k ? 'active' : ''}`} onClick={() => setParams({ tab: k })}>{label}</button>
        ))}
      </div>
      {tab === 'overview' && <Overview />}
      {tab === 'applications' && <Applications />}
      {tab === 'favorites' && <Favorites />}
      {tab === 'notifications' && <Notifications />}
      {tab === 'profile' && <Profile />}
    </div>
    </>
  );
}

function Overview() {
  const membership = useFetch(() => get('/me/memberships/current').catch(() => null));
  const apps = useFetch(() => get('/me/applications?size=100'));
  const favs = useFetch(() => get('/me/favorites'));
  const notes = useFetch(() => get('/me/notifications?size=1'));
  const m = membership.data;
  return (
    <div className="stats">
      <div className="card stat"><span className="stat-num">{pageOf(apps.data).total || (Array.isArray(apps.data) ? apps.data.length : 0)}</span>طلبات أتابعها</div>
      <div className="card stat"><span className="stat-num">{(favs.data || []).length}</span>فرص محفوظة</div>
      <div className="card stat"><span className="stat-num">{notes.data?.unreadCount ?? 0}</span>إشعارات غير مقروءة</div>
      <div className="card stat">
        <span className="stat-num">{m?.status === 'PAID' ? '✓' : '—'}</span>
        {m?.status === 'PAID' ? `عضوية فعّالة لغاية ${date(m.expiresAt)}` : <Link to="/membership">اشترك بالعضوية</Link>}
      </div>
    </div>
  );
}

function Applications() {
  const res = useFetch(() => get('/me/applications?size=100'));
  if (res.loading) return <Loading />;
  if (res.error) return <ErrorBox error={res.error} onRetry={res.reload} />;
  const items = Array.isArray(res.data) ? res.data : pageOf(res.data).items;
  if (!items.length) return <Empty>لسا ما ضفت أي طلب. افتح أي فرصة واضغط "تابع طلبي".</Empty>;

  const update = async (a, status) => {
    await put(`/me/applications/${a.id}`, { status, notes: a.notes, appliedAt: a.appliedAt, nextActionAt: a.nextActionAt });
    res.reload();
  };
  const remove = async (a) => { await del(`/me/applications/${a.id}`); res.reload(); };

  return (
    <div className="list">
      {items.map((a) => (
        <div key={a.id} className="card list-item">
          <div>
            <Link to={`/opportunities/${a.opportunity?.slug}`}><strong>{a.opportunity?.title}</strong></Link>
            <div className="muted small">{a.opportunity?.organization} · آخر موعد: {a.opportunity && deadlineText(a.opportunity)}</div>
          </div>
          <div className="row">
            <select value={a.status} onChange={(e) => update(a, e.target.value)}>
              {Object.entries(APP_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button className="btn btn-ghost btn-sm" onClick={() => remove(a)}>حذف</button>
          </div>
        </div>
      ))}
    </div>
  );
}

function Favorites() {
  const res = useFetch(() => get('/me/favorites'));
  if (res.loading) return <Loading />;
  if (res.error) return <ErrorBox error={res.error} onRetry={res.reload} />;
  if (!res.data?.length) return <Empty>ما في فرص محفوظة لسا.</Empty>;
  return (
    <div className="list">
      {res.data.map((f) => (
        <div key={f.id} className="card list-item">
          <div>
            <Link to={`/opportunities/${f.slug}`}><strong>{f.title}</strong></Link>
            <div className="muted small">{f.organization} · {f.country} · {FUNDING[f.funding]} · {deadlineText(f)}</div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={async () => { await del(`/me/favorites/${f.opportunityId}`); res.reload(); }}>إزالة</button>
        </div>
      ))}
    </div>
  );
}

function Notifications() {
  const res = useFetch(() => get('/me/notifications?size=50'));
  if (res.loading) return <Loading />;
  if (res.error) return <ErrorBox error={res.error} onRetry={res.reload} />;
  const items = pageOf(res.data?.items).items;
  return (
    <>
      {res.data?.unreadCount > 0 && (
        <button className="btn btn-outline btn-sm" onClick={async () => { await post('/me/notifications/read-all'); res.reload(); }}>تعليم الكل كمقروء</button>
      )}
      {!items.length ? <Empty>ما في إشعارات.</Empty> : (
        <div className="list">
          {items.map((n) => (
            <div key={n.id} className={`card list-item ${n.readAt ? '' : 'unread'}`}>
              <div>
                <strong>{n.title}</strong>
                <div className="small">{n.body}</div>
                <div className="muted small">{date(n.createdAt)}</div>
              </div>
              <div className="row">
                {!n.readAt && <button className="btn btn-outline btn-sm" onClick={async () => { await patch(`/me/notifications/${n.id}/read`); res.reload(); }}>مقروء</button>}
                <button className="btn btn-ghost btn-sm" onClick={async () => { await del(`/me/notifications/${n.id}`); res.reload(); }}>حذف</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

const PROFILE_FIELDS = [
  ['fullName', 'الاسم الكامل'],
  ['headline', 'نبذة قصيرة'],
  ['phone', 'الهاتف'],
  ['country', 'الدولة'],
  ['city', 'المدينة'],
  ['nationality', 'الجنسية'],
  ['degreeLevel', 'المرحلة الدراسية'],
  ['primarySpeciality', 'التخصص'],
  ['institution', 'الجامعة / المدرسة'],
];

function Profile() {
  const res = useFetch(() => get('/me/profile'));
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState('');
  if (res.loading) return <Loading />;
  if (res.error) return <ErrorBox error={res.error} onRetry={res.reload} />;
  const f = form || res.data;
  const set = (k) => (e) => setForm({ ...f, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault(); setMsg('');
    const body = {};
    PROFILE_FIELDS.forEach(([k]) => (body[k] = f[k] || null));
    Object.assign(body, {
      bio: f.bio || null,
      englishLevel: f.englishLevel || null,
      preferredFunding: f.preferredFunding || null,
      dateOfBirth: f.dateOfBirth || null,
      emailNotifications: !!f.emailNotifications,
      deadlineReminders: !!f.deadlineReminders,
    });
    try { await put('/me/profile', body); setMsg('تم الحفظ ✓'); res.reload(); setForm(null); } catch (e2) { setMsg(e2.message); }
  };

  return (
    <form className="card form form-grid" onSubmit={save}>
      {PROFILE_FIELDS.map(([k, label]) => (
        <Field key={k} label={label}><input value={f[k] || ''} onChange={set(k)} /></Field>
      ))}
      <Field label="تاريخ الميلاد"><input type="date" value={f.dateOfBirth || ''} onChange={set('dateOfBirth')} /></Field>
      <Field label="مستوى الإنجليزي">
        <select value={f.englishLevel || ''} onChange={set('englishLevel')}>
          <option value="">—</option>
          {['A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'NATIVE', 'OTHER'].map((l) => <option key={l}>{l}</option>)}
        </select>
      </Field>
      <Field label="التمويل المفضل">
        <select value={f.preferredFunding || ''} onChange={set('preferredFunding')}>
          <option value="">—</option>
          {Object.entries(FUNDING).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </Field>
      <Field label="عني"><textarea rows={3} value={f.bio || ''} onChange={set('bio')} /></Field>
      <label className="check"><input type="checkbox" checked={!!f.emailNotifications} onChange={(e) => setForm({ ...f, emailNotifications: e.target.checked })} /> إشعارات بالإيميل</label>
      <label className="check"><input type="checkbox" checked={!!f.deadlineReminders} onChange={(e) => setForm({ ...f, deadlineReminders: e.target.checked })} /> تذكير بالمواعيد النهائية</label>
      <div className="form-actions">
        <button className="btn btn-primary">حفظ</button>
        {msg && <span className="muted">{msg}</span>}
      </div>
    </form>
  );
}
