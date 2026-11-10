import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
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
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'log' ? 'log' : 'drafts';

  if (!isStaff(user)) {
    return (
      <div className="container section">
        <Empty>هاي الصفحة للأدمن بس.</Empty>
      </div>
    );
  }

  return (
    <>
      <PageHero dark title={tab === 'log' ? 'سجل Tab' : 'المسودات'}
        sub={tab === 'log'
          ? 'كل منشور بعته Tab من واتساب: إيمتى وصل، وين راح، وشو صار فيه.'
          : 'كل اللي وصل من واتساب (Tab): فرص ووظائف ودورات، بتستنى موافقتك قبل ما تنعرض بالموقع.'} />
      <div className="container section">
        <div className="tabs">
          <button className={`tab ${tab === 'drafts' ? 'active' : ''}`} onClick={() => setParams({})}>المسودات</button>
          <button className={`tab ${tab === 'log' ? 'active' : ''}`} onClick={() => setParams({ tab: 'log' })}>سجل Tab</button>
        </div>
        {tab === 'log' ? <IntakeLog /> : <Drafts />}
      </div>
    </>
  );
}

function Drafts() {
  const res = useFetch(() => get('/admin/intake/drafts'), []);
  const [filter, setFilter] = useState('');
  const all = res.data || [];
  const items = filter ? all.filter((d) => KINDS[d.kind]?.section === filter) : all;
  return (
    <>
      <Chips options={Object.values(KINDS).map((k) => k.section)} value={filter} onChange={setFilter} />
      {res.loading && <Loading />}
      {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
      {res.data && <p className="muted">{all.length} مسودة</p>}
      {res.data && !items.length && <Empty>ما في مسودات هلأ 🎉</Empty>}
      <div className="drafts">
        {items.map((d) => <DraftRow key={`${d.kind}-${d.id}`} d={d} onDone={res.reload} />)}
      </div>
    </>
  );
}

const RESULT = {
  created: ['انضاف', 'ok'],
  duplicate: ['مكرر', 'warn'],
  rejected: ['مرفوض', 'bad'],
};
const STATE = { DRAFT: 'مسودة', PUBLISHED: 'منشور ✓', DELETED: 'محذوف' };
const itemPath = (r) =>
  r.kind === 'JOB' ? `${CATALOG.JOB.path}/${r.itemSlug}` : r.kind === 'COURSE' ? `${CATALOG.COURSE.path}/${r.itemSlug}` : `/opportunities/${r.itemSlug}`;
const dateTime = (iso) =>
  new Date(iso).toLocaleString('ar-JO', { timeZone: 'Asia/Amman', year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });

function IntakeLog() {
  const [page, setPage] = useState(0);
  const res = useFetch(() => get(`/admin/intake/log?page=${page}&size=50`), [page]);
  const data = res.data;
  return (
    <>
      {res.loading && <Loading />}
      {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
      {data && !data.rows.length && <Empty>Tab لسا ما بعت اشي.</Empty>}
      {data && data.rows.length > 0 && (
        <>
          <p className="muted">{data.total} منشور وصل لحد هلأ</p>
          <div className="log-wrap">
            <table className="log-table">
              <thead>
                <tr><th>وصل</th><th>العنوان</th><th>القسم</th><th>النتيجة</th><th>هلأ</th></tr>
              </thead>
              <tbody>
                {data.rows.map((r) => {
                  const [label, tone] = RESULT[r.status] || [r.status, 'warn'];
                  return (
                    <tr key={r.id}>
                      <td className="nowrap">{dateTime(r.receivedAt)}</td>
                      <td>{r.title || <span className="muted">بدون عنوان</span>}</td>
                      <td className="nowrap">{r.kind ? KINDS[r.kind]?.section : '—'}</td>
                      <td><span className={`log-pill log-${tone}`}>{label}</span>{r.status !== 'created' && r.reason && <div className="muted log-reason">{r.reason}</div>}</td>
                      <td className="nowrap">
                        {r.state === 'PUBLISHED' && r.itemSlug ? <Link to={itemPath(r)}>{STATE.PUBLISHED}</Link>
                          : r.state === 'DRAFT' ? <Link to="/admin/drafts">{STATE.DRAFT}</Link>
                          : STATE[r.state] || '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <div className="row" style={{ justifyContent: 'center', marginTop: '1rem' }}>
              <button className="btn btn-outline btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>الأحدث</button>
              <span className="muted">{page + 1} / {data.totalPages}</span>
              <button className="btn btn-outline btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>الأقدم</button>
            </div>
          )}
        </>
      )}
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
