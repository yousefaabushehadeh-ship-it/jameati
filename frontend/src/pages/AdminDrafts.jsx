import { useState } from 'react';
import { del, get, post } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import { Empty, ErrorBox, Loading, PageHero, Chips } from '../components/ui';
import { CATALOG, FUNDING, date } from '../lib/format';

// صفحة الأدمن: كل اللي وصل من Tab (واتساب): فرص ووظائف ودورات،
// بتنشر كل وحدة بكبسة (بتروح على قسمها بالموقع) أو بتحذفها.
const STAFF = ['EDITOR', 'ADMIN', 'SUPER_ADMIN'];

const KINDS = {
  OPPORTUNITY: {
    label: 'فرصة', section: 'الفرص', tone: 'sky',
    categories: ['منح دراسية', 'تدريب', 'تطوع', 'زمالات', 'تبادل ثقافي', 'مؤتمرات', 'مسابقات'],
  },
  JOB: { label: 'وظيفة', section: 'الوظائف', tone: 'navy', categories: ['وظائف', ...CATALOG.JOB.categories] },
  COURSE: { label: 'دورة', section: 'الدورات', tone: 'gold', categories: CATALOG.COURSE.categories },
};

export const isStaff = (user) => !!user?.roles?.some((r) => STAFF.includes(r.replace(/^ROLE_/, '')));

export default function AdminDrafts() {
  const { user } = useAuth();
  const res = useFetch(() => get('/admin/intake/drafts'), []);
  const [filter, setFilter] = useState('');

  if (!isStaff(user)) {
    return (
      <div className="container section">
        <Empty>هاي الصفحة للأدمن بس.</Empty>
      </div>
    );
  }

  const all = res.data || [];
  const items = filter ? all.filter((d) => KINDS[d.kind]?.section === filter) : all;
  return (
    <>
      <PageHero dark title="المسودات" sub="كل اللي وصل من واتساب (Tab): فرص ووظائف ودورات، بتستنى موافقتك قبل ما تنعرض بالموقع." count={res.data ? `${all.length} مسودة` : undefined} />
      <div className="container section">
        <Chips options={Object.values(KINDS).map((k) => k.section)} value={filter} onChange={setFilter} />
        {res.loading && <Loading />}
        {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
        {res.data && !items.length && <Empty>ما في مسودات هلأ 🎉</Empty>}
        <div className="drafts">
          {items.map((d) => <DraftRow key={`${d.kind}-${d.id}`} d={d} onDone={res.reload} />)}
        </div>
      </div>
    </>
  );
}

function DraftRow({ d, onDone }) {
  const kind = KINDS[d.kind] || KINDS.OPPORTUNITY;
  const [category, setCategory] = useState(d.category);
  const [funding, setFunding] = useState(d.funding || 'PARTIALLY_FUNDED');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const categories = kind.categories.includes(d.category) ? kind.categories : [d.category, ...kind.categories];

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

  const publish = () =>
    act(() => post(`/admin/intake/drafts/${d.kind}/${d.id}/publish`, d.kind === 'OPPORTUNITY' ? { category, funding } : { category }));
  const remove = () => window.confirm('متأكد بدك تحذفها؟') && act(() => del(`/admin/intake/drafts/${d.kind}/${d.id}`));

  return (
    <article className="card draft">
      <div className="card-body">
        <div className="draft-head">
          <span className={`draft-kind draft-kind-${kind.tone}`}>{kind.label}</span>
          <h3 className="card-title">{d.title}</h3>
        </div>
        <div className="card-meta">
          <span>{d.organization}</span>
          {d.deadline && <span>آخر موعد: {/^\d{4}-\d{2}-\d{2}$/.test(d.deadline) ? date(d.deadline) : d.deadline}</span>}
          {d.sourceName && <span>المصدر: {d.sourceName === 'tab-whatsapp' ? 'واتساب (Tab)' : d.sourceName}</span>}
          <span>رح تنعرض بقسم: {kind.section}</span>
          <span>وصلت: {date(d.createdAt)}</span>
        </div>
        <p className="draft-text">{d.description}</p>
        {d.link && <a href={d.link} target="_blank" rel="noreferrer" className="draft-link">{d.link}</a>}
        <div className="draft-actions">
          <select value={category} onChange={(e) => setCategory(e.target.value)} aria-label="التصنيف">
            {categories.map((c) => <option key={c}>{c}</option>)}
          </select>
          {d.kind === 'OPPORTUNITY' && (
            <select value={funding} onChange={(e) => setFunding(e.target.value)} aria-label="التمويل">
              {Object.entries(FUNDING).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          )}
          <button className="btn btn-sky btn-sm" disabled={busy} onClick={publish}>نشر</button>
          <button className="btn btn-outline btn-sm" disabled={busy} onClick={remove}>حذف</button>
        </div>
        {err && <div className="alert alert-error">{err}</div>}
      </div>
    </article>
  );
}
