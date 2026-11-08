import { useSearchParams } from 'react-router-dom';
import { get, qs } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { usePaged } from '../lib/usePaged';
import { Chips, Empty, ErrorBox, Loading, MembershipBanner, OpportunityCard, PageHero } from '../components/ui';
import { FUNDING, STATUS, num } from '../lib/format';

export default function Opportunities() {
  const [params, setParams] = useSearchParams();
  const f = Object.fromEntries(params.entries());

  const cats = useFetch(() => get('/public/settings?namespace=categories'));
  const res = usePaged(
    (page) => get(`/public/opportunities${qs({ q: f.q, category: f.category, funding: f.funding, status: f.status, page, size: 12 })}`),
    [params.toString()]
  );
  const set = (k, v) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v); else next.delete(k);
    setParams(next, { replace: true });
  };
  const categories = (cats.data?.records || []).filter((c) => c.active !== false).map((c) => c.title);
  const free = res.items.filter((i) => !i.accessLocked).length;

  return (
    <>
      <PageHero
        count={res.total ? `${num(res.total)} فرصة` : '... فرصة'}
        title={f.category || 'الفرص والمنح'}
        sub="منح دراسية وتدريب وتطوع وزمالات، كلها من مصادرها الرسمية"
        placeholder="ابحث عن منحة، تدريب، دولة..."
        defaultValue={f.q || ''}
        onSearch={(v) => set('q', v)}
      />
      <div className="container section">
        <Chips options={categories} value={f.category || ''} onChange={(v) => set('category', v)} />
        <div className="filter-row">
          <select className="select-pill" value={f.funding || ''} onChange={(e) => set('funding', e.target.value)}>
            <option value="">كل أنواع التمويل</option>
            {Object.entries(FUNDING).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <select className="select-pill" value={f.status || ''} onChange={(e) => set('status', e.target.value)}>
            <option value="">كل الحالات</option>
            {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
        </div>
        {res.items.some((i) => i.accessLocked) && <MembershipBanner free={free} />}
        <div className="list-toolbar"><span className="muted">عرض {num(res.items.length)} من {num(res.total)} نتيجة</span></div>
        {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
        {!res.loading && !res.error && res.items.length === 0 && <Empty />}
        <div className="grid">{res.items.map((o) => <OpportunityCard key={o.id} o={o} />)}</div>
        {res.loading && <Loading />}
        {!res.loading && res.hasMore && <div className="pagination"><button className="btn btn-outline" onClick={res.more}>تحميل المزيد</button></div>}
      </div>
    </>
  );
}
