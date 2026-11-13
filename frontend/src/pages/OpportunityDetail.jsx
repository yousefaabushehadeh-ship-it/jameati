import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { get, post, del } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { useAuth } from '../lib/auth';
import { Badge, BrandMark, ErrorBox, Loading, LockedNotice } from '../components/ui';
import { FUNDING, STATUS, date, deadlineText, isClosed } from '../lib/format';
import AiChat from '../components/AiChat';
import Roadmap from '../components/Roadmap';

export default function OpportunityDetail() {
  const { slug } = useParams();
  const { user } = useAuth();
  const { data: o, loading, error, reload } = useFetch(() => get(`/public/opportunities/${slug}`), [slug]);
  const favs = useFetch(() => (user ? get('/me/favorites') : Promise.resolve([])), [user?.id]);
  const [msg, setMsg] = useState('');

  useEffect(() => {
    if (user && o?.id) post('/me/recently-viewed', { opportunityId: o.id }).catch(() => {});
  }, [user, o?.id]);

  if (loading) return <Loading />;
  if (error) return <div className="container section"><ErrorBox error={error} onRetry={reload} /></div>;

  const st = STATUS[o.status] || STATUS.UNKNOWN;
  const isFav = (favs.data || []).some((f) => f.opportunityId === o.id);

  const toggleFav = async () => {
    try {
      if (isFav) await del(`/me/favorites/${o.id}`); else await post(`/me/favorites/${o.id}`);
      favs.reload();
    } catch (e) { setMsg(e.message); }
  };
  const track = async () => {
    try {
      await post('/me/applications', { opportunityId: o.id, status: 'PLANNING' });
      setMsg('انضافت لمتابعة طلباتك في مساحتي ✓');
    } catch (e) { setMsg(e.message); }
  };

  return (
    <div className="container section detail">
      <Link to="/opportunities" className="back-link">→ رجوع للفرص</Link>
      {o.coverImage && <div className="detail-cover-wrap"><img className="detail-cover" src={o.coverImage} alt="" /><BrandMark className="brand-mark-lg" /></div>}
      <div className="row-wrap">
        {o.category && <Badge tone="sky">{o.category}</Badge>}
        <Badge tone={st.tone}>{st.label}</Badge>
        {o.funding && <Badge tone="blue">{FUNDING[o.funding]}</Badge>}
      </div>
      <h1>{o.title}</h1>
      <p className="lead">{o.shortDescription}</p>

      <div className="detail-grid">
        <article>
          {o.accessLocked ? <LockedNotice /> : (
            <div className="prose">{(o.description || '').split('\n').map((l, i) => l.trim() ? <p key={i}>{l}</p> : null)}</div>
          )}
          {!o.accessLocked && <Roadmap o={o} />}
          {!o.accessLocked && <AiChat path={`/public/opportunities/${o.id}/ai/chat`} />}
        </article>
        <aside className="card side">
          <dl>
            <dt>الجهة</dt><dd>{o.organization}</dd>
            <dt>الدولة</dt><dd>{o.country}</dd>
            <dt>آخر موعد</dt><dd>{deadlineText(o)}{o.daysRemaining > 0 && !o.alwaysOpen ? ` (باقي ${o.daysRemaining} يوم)` : ''}</dd>
            {o.degreeLevels?.length > 0 && (<><dt>المراحل</dt><dd>{o.degreeLevels.join('، ')}</dd></>)}
            {o.nationalities?.length > 0 && (<><dt>الجنسيات</dt><dd>{o.nationalities.join('، ')}</dd></>)}
            {o.lastVerifiedAt && (<><dt>آخر تحقق</dt><dd>{date(o.lastVerifiedAt)}</dd></>)}
          </dl>
          {isClosed(o) ? (
            <div className="alert alert-closed">التقديم على هالمنحة مغلق، انتهى آخر موعد.</div>
          ) : (o.applyUrl || o.officialUrl) && (
            <a className="btn btn-primary btn-block" href={o.applyUrl || o.officialUrl} target="_blank" rel="noreferrer">التقديم من الموقع الرسمي ↗</a>
          )}
          {user ? (
            <>
              <button className="btn btn-outline btn-block" onClick={toggleFav}>{isFav ? '★ محفوظة' : '☆ احفظ بالمفضلة'}</button>
              <button className="btn btn-outline btn-block" onClick={track}>📋 تابع طلبي لهالفرصة</button>
            </>
          ) : (
            <Link className="btn btn-outline btn-block" to="/login" state={{ from: `/opportunities/${slug}` }}>سجّل دخول عشان تحفظها</Link>
          )}
          {msg && <p className="muted small">{msg}</p>}
          {!isClosed(o) && (
            <div className="service-box">
              <strong>بدك تقدّم؟</strong>
              <p className="muted small">استشارة مع فريق جامعتي، مراجعة أوراقك، والتقديم معك لحد ما يوصل طلبك.</p>
              <Link className="btn btn-navy btn-block" to="/membership?plan=CONSULTATION">الاستشارة والتقديم (مدفوعة)</Link>
            </div>
          )}
          {o.sourceUrl && <p className="small muted">المصدر: <a href={o.sourceUrl} target="_blank" rel="noreferrer">{o.sourceName || 'الموقع الرسمي'}</a></p>}
        </aside>
      </div>
    </div>
  );
}
