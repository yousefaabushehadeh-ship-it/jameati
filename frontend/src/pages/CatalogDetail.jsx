import { Fragment } from 'react';
import { Link, useParams } from 'react-router-dom';
import { get } from '../lib/api';
import { useFetch } from '../lib/useFetch';
import { Badge, ErrorBox, Loading, LockedNotice } from '../components/ui';
import { CATALOG } from '../lib/format';
import AiChat from '../components/AiChat';

function renderValue(v) {
  if (v == null) return '';
  if (Array.isArray(v)) return v.map((x) => (typeof x === 'object' ? JSON.stringify(x) : x)).join('، ');
  if (typeof v === 'object') return JSON.stringify(v);
  if (typeof v === 'boolean') return v ? 'نعم' : 'لا';
  return String(v);
}

export default function CatalogDetail({ type }) {
  const { slug } = useParams();
  const { data: it, loading, error, reload } = useFetch(() => get(`/public/catalog/${type}/${slug}`), [type, slug]);
  if (loading) return <Loading />;
  if (error) return <div className="container section"><ErrorBox error={error} onRetry={reload} /></div>;

  const attrs = Object.entries(it.attributes || {}).filter(([, v]) => v !== null && v !== '' && (typeof v !== 'object' || Array.isArray(v)));

  return (
    <div className="container section detail">
      <Link to={CATALOG[type].path} className="back-link">→ رجوع لـ{CATALOG[type].nav}</Link>
      {it.image && <img className="detail-cover" src={it.image} alt="" />}
      <div className="row-wrap">
        <Badge tone="sky">{CATALOG[type].single}</Badge>
        {it.category && <Badge>{it.category}</Badge>}
      </div>
      <h1>{it.title}</h1>
      {it.summary && <p className="lead">{it.summary}</p>}
      <div className="detail-grid">
        <article>
          {it.accessLocked ? <LockedNotice /> : (
            <div className="prose">{(it.content || '').split('\n').map((l, i) => l.trim() ? <p key={i}>{l}</p> : null)}</div>
          )}
          {type === 'JOB' && !it.accessLocked && <AiChat path={`/public/catalog/JOB/${it.id}/ai/chat`} />}
        </article>
        <aside className="card side">
          <dl>
            {it.organization && (<><dt>الجهة</dt><dd>{it.organization}</dd></>)}
            {it.country && (<><dt>الدولة</dt><dd>{it.country}</dd></>)}
            {it.rating != null && (<><dt>التقييم</dt><dd>⭐ {it.rating}</dd></>)}
            {attrs.slice(0, 12).map(([k, v]) => (<Fragment key={k}><dt>{k}</dt><dd>{renderValue(v)}</dd></Fragment>))}
          </dl>
          {it.website && <a className="btn btn-primary btn-block" href={it.website} target="_blank" rel="noreferrer">الموقع الرسمي ↗</a>}
        </aside>
      </div>
    </div>
  );
}
