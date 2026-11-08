import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { get, pageOf } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import Icon from '../components/Icon';
import { CatalogCard, Loading, LockedStrip, OpportunityCard, SectionHead } from '../components/ui';
import { CATALOG } from '../lib/format';

function CatalogSection({ type, wide }) {
  const cfg = CATALOG[type];
  const res = useFetch(() => get(`/public/catalog/${type}/homepage?requestedLimit=${wide ? 2 : 4}`), [type]);
  const items = pageOf(res.data).items;
  if (!res.loading && !items.length) return null;
  return (
    <section className="container section">
      <SectionHead icon={cfg.icon} eyebrow={cfg.eyebrow} title={cfg.homeTitle} sub={cfg.homeSub} more={cfg.more} moreTo={cfg.path} />
      {res.loading ? <Loading /> : (
        <div className={`grid ${wide ? 'grid-2' : ''}`}>{items.map((it) => <CatalogCard key={it.id} type={type} item={it} />)}</div>
      )}
    </section>
  );
}

export default function Home() {
  const [q, setQ] = useState('');
  const nav = useNavigate();
  const { user } = useAuth();
  const featured = useFetch(() => get('/public/opportunities/featured?limit=4'));
  const cats = useFetch(() => get('/public/settings?namespace=categories'));
  const plan = useFetch(() => get('/public/membership-plan'), [user?.id]);
  const all = useFetch(() => get('/public/opportunities?size=1'));

  const categories = (cats.data?.records || []).filter((c) => c.active !== false);
  const totalOpp = pageOf(all.data).total;
  const lockedCount = plan.data && !plan.data.active && totalOpp > (plan.data.freeLimit || 0) ? totalOpp - plan.data.freeLimit : 0;

  return (
    <>
      <section className="home-hero">
        <div className="container home-hero-inner">
          <div className="home-hero-copy">
            <span className="hero-count"><Icon name="sparkle" size={16} /> منصة الفرص التعليمية والمهنية</span>
            <h1>كل فرصة تحتاجها لرحلتك الجامعية والمهنية، بمكان واحد</h1>
            <p>منح ودورات وجامعات وتخصصات ووظائف من مصادرها الرسمية، مع أدوات تساعدك تخطط وتقدّم بثقة.</p>
            <form className="hero-search" onSubmit={(e) => { e.preventDefault(); nav(`/opportunities?q=${encodeURIComponent(q)}`); }}>
              <Icon name="search" size={22} className="hero-search-icon" />
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="ابحث عن منحة، دورة، جامعة..." />
              <button className="btn btn-sky">بحث</button>
            </form>
            <div className="hero-cats">
              {categories.slice(0, 6).map((c) => (
                <Link key={c.id} to={`/opportunities?category=${encodeURIComponent(c.title)}`}>{c.title}</Link>
              ))}
            </div>
          </div>
          <div className="home-hero-tiles" aria-hidden="true">
            {['UNIVERSITY', 'SPECIALITY', 'COURSE', 'JOB'].map((t) => (
              <Link key={t} to={CATALOG[t].path} className="hero-tile">
                <Icon name={CATALOG[t].icon} size={28} />
                <strong>{CATALOG[t].nav}</strong>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="container section">
        <SectionHead icon="sparkle" eyebrow="الفرص المختارة" title="فرص مميزة لا تفوّتها" sub="اختيارات محدّثة من المصادر الرسمية." more="استكشف كل الفرص" moreTo="/opportunities" />
        {featured.loading ? <Loading /> : (
          <div className="grid">{(featured.data || []).map((o) => <OpportunityCard key={o.id} o={o} />)}</div>
        )}
        {lockedCount > 0 && <LockedStrip count={lockedCount} />}
      </section>

      <CatalogSection type="COURSE" />
      <CatalogSection type="UNIVERSITY" wide />
      <CatalogSection type="SPECIALITY" />

      {!user && (
        <section className="container section">
          <div className="cta-gold">
            <h2>ابدأ رحلتك اليوم</h2>
            <p>أنشئ حسابك مجانًا، احفظ الفرص، وتابع طلباتك ومواعيدك بمكان واحد.</p>
            <Link to="/register" className="btn btn-white btn-lg">أنشئ حسابك مجانًا <Icon name="arrow" size={18} /></Link>
          </div>
        </section>
      )}
    </>
  );
}
