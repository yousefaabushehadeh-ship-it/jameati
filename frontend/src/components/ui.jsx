import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { FUNDING, STATUS, deadlineText, CATALOG, num } from '../lib/format';
import Icon from './Icon';

export const Loading = () => <div className="loading"><span className="spinner" /> جاري التحميل…</div>;

export const ErrorBox = ({ error, onRetry }) => (
  <div className="alert alert-error">
    {error?.message || 'صار خطأ'}
    {onRetry && <button className="btn btn-outline btn-sm" onClick={onRetry}>إعادة المحاولة</button>}
  </div>
);

export const Empty = ({ children = 'لا توجد نتائج' }) => <div className="empty">{children}</div>;

export function Badge({ tone = 'gray', children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

// لوجو جامعتي فوق صور المنح
export const BrandMark = ({ className = '' }) => (
  <span className={`brand-mark ${className}`} aria-hidden="true"><img src="/logo.png" alt="" /></span>
);

// صورة الكرت: صورة حقيقية لو موجودة، وإلا خلفية متدرجة بأيقونة القسم
function Media({ image, icon, locked, chip, extra, brand }) {
  return (
    <div className={`media ${locked ? 'media-locked' : ''}`}>
      {image ? <img src={image} alt="" loading="lazy" /> : <div className="media-ph"><Icon name={icon} size={56} stroke={1.4} /></div>}
      {chip && <span className="media-chip">{chip}</span>}
      <span className="media-icon"><Icon name={icon} size={22} /></span>
      {locked && <span className="media-lock"><Icon name="lock" size={13} /> للأعضاء</span>}
      {brand && <BrandMark />}
      {extra}
    </div>
  );
}

function LockedFoot() {
  return (
    <>
      <div className="skeleton"><span /><span /></div>
      <div className="card-foot">
        <span className="muted-xs"><Icon name="lock" size={15} /> تفاصيل محمية</span>
        <Link to="/membership" className="btn btn-navy btn-sm" onClick={(e) => e.stopPropagation()}>اشترك وافتح <Icon name="arrow" size={14} /></Link>
      </div>
    </>
  );
}

export function OpportunityCard({ o }) {
  const st = STATUS[o.status] || STATUS.UNKNOWN;
  const to = `/opportunities/${o.slug}`;
  return (
    <article className="card item-card">
      <Link to={to} className="stretched" aria-label={o.title} />
      <Media image={o.coverImage} icon="cap" locked={o.accessLocked} chip={o.category} brand
        extra={!o.accessLocked && <span className={`media-status st-${st.tone}`}>{st.label}</span>} />
      <div className="card-body">
        <h3 className="card-title">{o.title}</h3>
        {o.accessLocked ? <LockedFoot /> : (
          <>
            {o.shortDescription && <p className="card-text">{o.shortDescription}</p>}
            <div className="card-meta">
              {o.funding && <span><Icon name="check" size={15} /> {FUNDING[o.funding]}</span>}
              <span><Icon name="calendar" size={15} /> {deadlineText(o)}</span>
            </div>
            <div className="card-foot">
              <span className="muted-xs"><Icon name="pin" size={15} /> {o.country || 'عالمي'}</span>
              <span className="link-sky">التفاصيل <Icon name="arrow" size={14} /></span>
            </div>
          </>
        )}
      </div>
    </article>
  );
}

export function CatalogCard({ type, item }) {
  const cfg = CATALOG[type];
  const to = `${cfg.path}/${item.slug}`;
  return (
    <article className="card item-card">
      <Link to={to} className="stretched" aria-label={item.title} />
      <Media image={item.image} icon={cfg.icon} locked={item.accessLocked} chip={item.category} />
      <div className="card-body">
        <h3 className="card-title">{item.title}</h3>
        {item.accessLocked ? <LockedFoot /> : (
          <>
            {item.summary && <p className="card-text">{item.summary}</p>}
            <div className="card-foot">
              <span className="muted-xs"><Icon name="pin" size={15} /> {item.country || 'عالمي'}</span>
              <span className="link-sky">التفاصيل <Icon name="arrow" size={14} /></span>
            </div>
          </>
        )}
      </div>
    </article>
  );
}

export function SectionHead({ icon, eyebrow, title, sub, more, moreTo }) {
  return (
    <div className="section-head">
      <div className="section-title">
        <span className="tile-icon"><Icon name={icon} size={24} /></span>
        <div>
          {eyebrow && <span className="eyebrow">{eyebrow}</span>}
          <h2>{title}</h2>
          {sub && <p className="muted">{sub}</p>}
        </div>
      </div>
      {more && <Link to={moreTo} className="link-sky">{more} <Icon name="arrow" size={16} /></Link>}
    </div>
  );
}

export function PageHero({ count, title, sub, placeholder, defaultValue, onSearch, dark, art }) {
  return (
    <section className={`page-hero ${dark ? 'page-hero-dark' : ''} ${art ? 'has-art' : ''}`}>
      {art && <img className="hero-art" src={art} alt="" aria-hidden="true" />}
      <div className="container">
        {count && <span className="hero-count">{count}</span>}
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
        {onSearch && (
          <form className="hero-search" onSubmit={(e) => { e.preventDefault(); onSearch(new FormData(e.currentTarget).get('q')); }}>
            <Icon name="search" size={22} className="hero-search-icon" />
            <input name="q" key={defaultValue} defaultValue={defaultValue} placeholder={placeholder} />
            <button className="btn btn-sky">بحث</button>
          </form>
        )}
      </div>
    </section>
  );
}

export function Chips({ options, value, onChange }) {
  return (
    <div className="chips">
      <button className={`chip ${!value ? 'active' : ''}`} onClick={() => onChange('')}>الكل</button>
      {options.map((o) => {
        const [v, l] = Array.isArray(o) ? o : [o, o];
        return <button key={v} className={`chip ${value === v ? 'active' : ''}`} onClick={() => onChange(v)}>{l}</button>;
      })}
    </div>
  );
}

export function MembershipBanner({ free }) {
  return (
    <div className="member-banner">
      <div>
        <span className="pill-ghost"><Icon name="crown" size={15} /> عضوية جامعتي+</span>
        <h3>{free ? `استكشف ${num(free)} اختيارات مجانية، وافتح الباقي بعضوية واحدة` : 'افتح كل المحتوى بعضوية واحدة'}</h3>
        <div className="member-banner-meta">
          <span><Icon name="check" size={16} /> اختيارات متاحة مجانًا</span>
          <span><Icon name="lock" size={16} /> المحتوى الكامل والمصادر للأعضاء</span>
        </div>
      </div>
      <Link to="/membership" className="btn btn-white btn-lg">اختر باقتك وشاهد الكل <Icon name="arrow" size={16} /></Link>
    </div>
  );
}

export function LockedStrip({ count }) {
  return (
    <div className="locked-strip">
      <div className="locked-strip-text">
        <span className="tile-icon tile-navy"><Icon name="lock" size={20} /></span>
        <div>
          <strong>يوجد {num(count)} عنصرًا إضافيًا ضمن العضوية</strong>
          <span className="muted-xs">وصول كامل لمدة سنة مع الباقة السنوية.</span>
        </div>
      </div>
      <Link to="/membership" className="btn btn-sky">افتح الكل <Icon name="arrow" size={16} /></Link>
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1 || page >= totalPages - 1) return null;
  return (
    <div className="pagination">
      <button className="btn btn-outline" onClick={() => onChange(page + 1)}>تحميل المزيد</button>
    </div>
  );
}

export function LockedNotice() {
  return (
    <div className="locked-strip">
      <div className="locked-strip-text">
        <span className="tile-icon tile-navy"><Icon name="lock" size={20} /></span>
        <div>
          <strong>التفاصيل الكاملة متاحة للأعضاء</strong>
          <span className="muted-xs">اشترك بعضوية جامعتي+ لتشوف كل المحتوى والمصادر.</span>
        </div>
      </div>
      <Link to="/membership" className="btn btn-sky">اشترك وافتح <Icon name="arrow" size={16} /></Link>
    </div>
  );
}

export function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />;
  return children;
}

export function Field({ label, children, hint }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}
