import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { post } from '../lib/api';
import Icon from './Icon';

const OPP_MENU = [
  ['/opportunities', 'كل الفرص'],
  ['/opportunities?category=منح دراسية', 'المنح الدراسية'],
  ['/opportunities?category=تدريب', 'التدريب'],
  ['/opportunities?category=تطوع', 'التطوع'],
  ['/opportunities?category=زمالات', 'الزمالات'],
  ['/opportunities?category=مؤتمرات', 'المؤتمرات'],
];
const TOOLS_MENU = [
  ['/specialities', 'دليل التخصصات'],
  ['/me', 'لوحة المستخدم'],
  ['/me?tab=favorites', 'الفرص المحفوظة'],
];

function Dropdown({ label, items, activePrefix }) {
  const [open, setOpen] = useState(false);
  const loc = useLocation();
  const ref = useRef(null);
  useEffect(() => { setOpen(false); }, [loc]);
  useEffect(() => {
    const close = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, []);
  const active = activePrefix && loc.pathname.startsWith(activePrefix);
  return (
    <div className="dd" ref={ref} onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button className={`nav-link ${active ? 'active' : ''}`} onClick={() => setOpen(!open)} aria-expanded={open}>
        {label} <Icon name="chevron" size={14} />
      </button>
      <div className={`dd-menu ${open ? 'open' : ''}`}>
        {items.map(([to, l]) => <Link key={to} to={to}>{l}</Link>)}
      </div>
    </div>
  );
}

function Header() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => { setOpen(false); }, [loc]);

  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="brand" aria-label="جامعتي">
          <img src="/logo.png" alt="جامعتي" width="130" height="52" />
        </Link>
        <nav className={`nav ${open ? 'open' : ''}`}>
          <Dropdown label="الفرص" items={OPP_MENU} activePrefix="/opportunities" />
          <NavLink className="nav-link" to="/courses">الدورات</NavLink>
          <NavLink className="nav-link" to="/articles">المقالات</NavLink>
          <NavLink className="nav-link" to="/jobs">الوظائف</NavLink>
          <NavLink className="nav-link" to="/universities">الجامعات</NavLink>
          <NavLink className="nav-link" to="/organizations">المؤسسات</NavLink>
          <Dropdown label="أدوات المسار" items={TOOLS_MENU} />
          <Link to="/membership" className="btn btn-gold btn-pill"><Icon name="crown" size={16} /> جامعتي+</Link>
        </nav>
        <div className="header-actions">
          <Link to="/opportunities" className="icon-btn" aria-label="بحث"><Icon name="search" /></Link>
          {user ? (
            <>
              <Link to="/me" className="btn btn-white">مساحتي</Link>
              <button className="btn btn-sky" onClick={async () => { await logout(); nav('/'); }}>خروج</button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn btn-white">تسجيل الدخول</Link>
              <Link to="/register" className="btn btn-sky">ابدأ مجانًا</Link>
            </>
          )}
          <button className="icon-btn menu-btn" aria-label="القائمة" onClick={() => setOpen(!open)}>
            <Icon name={open ? 'close' : 'menu'} />
          </button>
        </div>
      </div>
    </header>
  );
}

function Newsletter() {
  const [email, setEmail] = useState('');
  const [state, setState] = useState({ open: false, msg: '', err: '' });
  const submit = async (e) => {
    e.preventDefault();
    try {
      await post('/public/newsletter/subscriptions', { email, frequency: 'weekly', topics: ['opportunities'], consent: true });
      setState({ open: true, msg: 'تم الاشتراك ✓ رح توصلك الفرص المهمة.', err: '' });
    } catch (e2) { setState({ open: true, msg: '', err: e2.message }); }
  };
  return (
    <div className="newsletter">
      <div className="newsletter-text">
        <span className="tile-icon tile-sky"><Icon name="mail" size={24} /></span>
        <div>
          <h3>الفرص المهمة تصل إليك</h3>
          <p>اختر اهتماماتك وتكرار الرسائل من صفحة النشرة.</p>
        </div>
      </div>
      {state.open ? (
        state.msg ? <p className="newsletter-ok">{state.msg}</p> : (
          <form className="newsletter-form" onSubmit={submit}>
            <input type="email" required placeholder="name@example.com" value={email} onChange={(e) => setEmail(e.target.value)} dir="ltr" />
            <button className="btn btn-white">اشترك</button>
            {state.err && <span className="newsletter-err">{state.err}</span>}
          </form>
        )
      ) : (
        <button className="btn btn-white" onClick={() => setState({ ...state, open: true })}>اشترك في النشرة <Icon name="arrow" size={16} /></button>
      )}
    </div>
  );
}

function Footer() {
  const cols = [
    ['اكتشف', [['/opportunities', 'كل الفرص'], ['/opportunities?category=منح دراسية', 'المنح الدراسية'], ['/opportunities?category=تدريب', 'تدريب'], ['/courses', 'الدورات'], ['/jobs', 'الوظائف'], ['/articles', 'المقالات']]],
    ['الأدلة', [['/universities', 'الجامعات'], ['/organizations', 'المؤسسات'], ['/specialities', 'التخصصات']]],
    ['أدواتك', [['/membership', 'عضوية جامعتي+'], ['/me', 'لوحة المستخدم'], ['/me?tab=favorites', 'الفرص المحفوظة']]],
    ['جامعتي', [['/contact', 'تواصل معنا'], ['/contact', 'الأسئلة الشائعة']]],
  ];
  return (
    <footer className="footer">
      <div className="container">
        <Newsletter />
        <div className="footer-grid">
          <div className="footer-about">
            <img src="/logo.png" alt="جامعتي" width="150" height="60" />
            <p>مساحة عربية موثوقة تجمع فرص التعلم والعمل وتمنحك أدوات عملية لتنظيم رحلتك من الاكتشاف حتى القبول.</p>
            <Link to="/opportunities" className="link-sky">ابحث في المنصة <Icon name="arrow" size={16} /></Link>
            <div className="socials">
              <a href="#" aria-label="Instagram"><Icon name="instagram" size={18} /></a>
              <a href="#" aria-label="TikTok"><Icon name="tiktok" size={18} /></a>
              <a href="#" aria-label="WhatsApp"><Icon name="whatsapp" size={18} /></a>
            </div>
          </div>
          {cols.map(([title, links]) => (
            <div key={title} className="footer-col">
              <h4>{title}</h4>
              {links.map(([to, l]) => <Link key={l} to={to}>{l}</Link>)}
            </div>
          ))}
        </div>
        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} جامعتي — جميع الحقوق محفوظة.</span>
          <span>تحقق دائمًا من الشروط والمواعيد في المصدر الرسمي.</span>
        </div>
      </div>
    </footer>
  );
}

export default function Layout() {
  const loc = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [loc.pathname]);
  return (
    <div className="app">
      <div className="announce">✨ فرص جديدة كل أسبوع — حدّث تفضيلاتك لتحصل على ترشيحات أدق</div>
      <Header />
      <main><Outlet /></main>
      <Footer />
    </div>
  );
}
