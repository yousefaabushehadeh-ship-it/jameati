import { useSearchParams } from 'react-router-dom';
import { get, qs } from '../lib/api';
import { usePaged } from '../lib/usePaged';
import { CatalogCard, Chips, Empty, ErrorBox, Loading, LockedStrip, MembershipBanner, PageHero } from '../components/ui';
import { CATALOG, num } from '../lib/format';

const SORTS = [['createdAt,desc', 'الأحدث'], ['views,desc', 'الأكثر مشاهدة'], ['rating,desc', 'الأعلى تقييمًا']];

export default function CatalogList({ type }) {
  const cfg = CATALOG[type];
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const category = params.get('category') || '';
  const sort = params.get('sort') || 'createdAt,desc';

  const res = usePaged(
    (page) => get(`/public/catalog/${type}${qs({
      q, category, page, size: 12, sort: cfg.sorts ? sort : undefined,
      countryMix: type === 'UNIVERSITY' || undefined, easyFirst: type === 'JOB' || undefined,
    })}`),
    [type, q, category, sort]
  );

  const set = (k, v) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, v); else next.delete(k);
    setParams(next, { replace: true });
  };
  const free = res.items.filter((i) => !i.accessLocked).length;
  const locked = res.items.some((i) => i.accessLocked);

  return (
    <>
      <PageHero
        count={res.total ? `${num(res.total)} ${cfg.unit}` : `... ${cfg.unit}`}
        title={cfg.title}
        sub={cfg.subtitle}
        placeholder={cfg.search}
        defaultValue={q}
        onSearch={(v) => set('q', v)}
      />
      <div className="container section">
        {cfg.categories.length > 0 && <Chips options={cfg.categories} value={category} onChange={(v) => set('category', v)} />}
        {locked && <MembershipBanner free={free} />}
        <div className="list-toolbar">
          <span className="muted">عرض {num(res.items.length)} من {num(res.total)} نتيجة</span>
          {cfg.sorts && (
            <div className="chips chips-sm">
              {SORTS.map(([v, l]) => <button key={v} className={`chip ${sort === v ? 'active-soft' : ''}`} onClick={() => set('sort', v)}>{l}</button>)}
            </div>
          )}
        </div>
        {res.error && <ErrorBox error={res.error} onRetry={res.reload} />}
        {!res.loading && !res.error && res.items.length === 0 && <Empty />}
        <div className="grid">{res.items.map((it) => <CatalogCard key={it.id} type={type} item={it} />)}</div>
        {res.loading && <Loading />}
        {!res.loading && res.hasMore && (
          <div className="pagination"><button className="btn btn-outline" onClick={res.more}>تحميل المزيد</button></div>
        )}
        {locked && !res.hasMore && !res.loading && res.total > free && <LockedStrip count={res.total - free} />}
      </div>
    </>
  );
}
