import { useState } from 'react';
import { del, get, pageOf, post } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import { Empty, ErrorBox, Loading, PageHero } from '../components/ui';
import { FUNDING, date } from '../lib/format';

// صفحة بسيطة للأدمن: الفرص اللي وصلت من Tab (واتساب) أو انحفظت كمسودة،
// بتقدر تنشرها بكبسة أو تحذفها.
const STAFF = ['EDITOR', 'ADMIN', 'SUPER_ADMIN'];
const CATEGORIES = ['منح دراسية', 'تدريب', 'تطوع', 'زمالات', 'تبادل ثقافي', 'مؤتمرات', 'وظائف'];

export const isStaff = (user) => !!user?.roles?.some((r) => STAFF.includes(r.replace(/^ROLE_/, '')));

export default function AdminDrafts() {
  const { user } = useAuth();
  const res = useFetch(() => get('/admin/opportunities/drafts?size=50'), []);

  if (!isStaff(user)) {
    return (
      <div className="container section">
        <Empty>هاي الصفحة للأدمن بس.</Empty>
      </div>
    );
  }

  const { items, total } = pageOf(res.data);
  return (
    <>
      <PageHero dark title="مسودات الفرص" sub="الفرص اللي وصلت من واتساب (Tab) وبتستنى موافقتك قبل ما تنعرض بالموقع." count={res.data ? `${total} مسودة` : undefined} />
      <div className="container section">
        {res.loading && <Loading />}
        {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
        {res.data && !items.length && <Empty>ما في مسودات هلأ 🎉</Empty>}
        <div className="drafts">
          {items.map((o) => <DraftRow key={o.id} o={o} onDone={res.reload} />)}
        </div>
      </div>
    </>
  );
}

function DraftRow({ o, onDone }) {
  const [category, setCategory] = useState(o.category);
  const [funding, setFunding] = useState(o.funding);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const categories = CATEGORIES.includes(o.category) ? CATEGORIES : [o.category, ...CATEGORIES];

  const act = async (fn) => {
    setBusy(true);
    setErr('');
    try {
      await fn();
      onDone();
    } catch (e) {
      setErr(e.message);
      setBusy(false);
    }
  };

  return (
    <article className="card draft">
      <div className="card-body">
        <h3 className="card-title">{o.title}</h3>
        <div className="card-meta">
          <span>{o.organization}</span>
          {o.deadline && <span>آخر موعد: {date(o.deadline)}</span>}
          {!o.deadline && o.deadlineText && <span>آخر موعد: {o.deadlineText}</span>}
          {o.sourceName && <span>المصدر: {o.sourceName === 'tab-whatsapp' ? 'واتساب (Tab)' : o.sourceName}</span>}
          <span>وصلت: {date(o.createdAt)}</span>
        </div>
        <p className="draft-text">{o.description}</p>
        {o.officialUrl && <a href={o.officialUrl} target="_blank" rel="noreferrer" className="draft-link">{o.officialUrl}</a>}
        <div className="draft-actions">
          <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="التصنيف">
            {categories.map((c) => <option key={c}>{c}</option>)}
          </select>
          <select value={funding} onChange={(e) => setFunding(e.target.value)} aria-label="التمويل">
            {Object.entries(FUNDING).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <button className="btn btn-sky btn-sm" disabled={busy}
            onClick={() => act(() => post(`/admin/opportunities/${o.id}/publish`, { category, funding }))}>
            نشر
          </button>
          <button className="btn btn-outline btn-sm" disabled={busy}
            onClick={() => window.confirm('متأكد بدك تحذفها؟') && act(() => del(`/admin/opportunities/${o.id}`))}>
            حذف
          </button>
        </div>
        {err && <div className="alert alert-error">{err}</div>}
      </div>
    </article>
  );
}
