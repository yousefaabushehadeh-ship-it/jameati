import { Link } from 'react-router-dom';
import Icon from './Icon';
import { date, isClosed } from '../lib/format';

// أوراق بتنطلب عادةً حسب نوع الفرصة، بتطلع لما الأدمن ما يكون معبّي الأوراق الخاصة بالمنحة
const COMMON_DOCS = {
  default: ['جواز سفر ساري', 'سيرة ذاتية (CV)', 'رسالة دافع (Motivation Letter)'],
  study: ['جواز سفر ساري', 'كشف العلامات والشهادة الأخيرة (مصدّقة ومترجمة)', 'شهادة لغة (IELTS / TOEFL أو حسب الإعلان)', 'سيرة ذاتية (CV)', 'رسالة دافع (Motivation Letter)', 'رسائل توصية (عادةً 2)'],
  volunteer: ['جواز سفر ساري', 'سيرة ذاتية (CV)', 'رسالة دافع (Motivation Letter)'],
};
const STUDY = ['منح', 'منحة', 'برامج جامعية', 'زمالات', 'تبادل ثقافي'];

const lines = (s) => (s || '').split('\n').map((l) => l.trim()).filter(Boolean);

// بنسحب فقرة "الأهلية" من الوصف إذا موجودة
function eligibilityFromDescription(desc) {
  const rows = lines(desc);
  const i = rows.findIndex((l) => l.length < 30 && l.includes('الأهلية'));
  if (i < 0 || !rows[i + 1]) return [];
  return [rows[i + 1]];
}

function commonDocs(category = '') {
  if (STUDY.some((k) => category.includes(k))) return COMMON_DOCS.study;
  if (category.includes('تطوع')) return COMMON_DOCS.volunteer;
  return COMMON_DOCS.default;
}

export default function Roadmap({ o }) {
  const docs = lines(o.requiredDocuments);
  const docsAreCommon = docs.length === 0;
  const docList = docsAreCommon ? commonDocs(o.category) : docs;

  const reqs = [
    ...lines(o.requirements),
    ...(o.degreeLevels?.length ? [`المرحلة: ${o.degreeLevels.join('، ')}`] : []),
    ...(o.nationalities?.length ? [`الجنسيات: ${o.nationalities.join('، ')}`] : []),
    ...(o.specialities?.length ? [`التخصصات: ${o.specialities.slice(0, 6).join('، ')}`] : []),
  ];
  if (!lines(o.requirements).length) reqs.push(...eligibilityFromDescription(o.description));

  const closed = isClosed(o);
  const deadline = o.alwaysOpen ? 'التقديم مفتوح دائمًا' : o.deadline ? `آخر موعد: ${date(o.deadline)}` : (o.deadlineText || 'الموعد غير محدد، تأكد من الإعلان الرسمي');
  const link = o.applyUrl || o.officialUrl;

  const steps = [
    {
      icon: 'check', title: 'تأكد إنك مؤهل',
      body: reqs.length ? <ul>{reqs.map((r) => <li key={r}>{r}</li>)}</ul> : <p>اقرأ شروط الأهلية بالإعلان الرسمي قبل ما تبلش.</p>,
    },
    {
      icon: 'file', title: 'جهّز الشهادات والأوراق',
      body: (
        <>
          <ul className="roadmap-docs">{docList.map((d) => <li key={d}><Icon name="check" size={14} /> {d}</li>)}</ul>
          {docsAreCommon && <p className="muted-xs">هاي الأوراق اللي بتنطلب عادةً بهالنوع من الفرص. القائمة النهائية بتكون بالإعلان الرسمي.</p>}
        </>
      ),
    },
    {
      icon: 'calendar', title: closed ? 'التقديم مغلق' : 'قدّم قبل الموعد',
      body: (
        <p>
          {deadline}
          {!closed && o.daysRemaining > 0 && !o.alwaysOpen ? ` (باقي ${o.daysRemaining} يوم)` : ''}
          {!closed && link && <> · <a href={link} target="_blank" rel="noreferrer">رابط التقديم الرسمي ↗</a></>}
        </p>
      ),
    },
    {
      icon: 'cap', title: 'بدك حدا يمشي معك؟',
      body: <p>فريق جامعتي بيراجع أوراقك وبيقدّم معك خطوة بخطوة. <Link to="/membership?plan=CONSULTATION">الاستشارة والتقديم ←</Link></p>,
    },
  ];

  return (
    <section className="card roadmap">
      <h2><Icon name="pin" size={20} /> خريطة المنحة</h2>
      <ol>
        {steps.map((s, i) => (
          <li key={s.title} className={closed && i === 2 ? 'roadmap-closed' : ''}>
            <span className="roadmap-num">{i + 1}</span>
            <div><h3>{s.title}</h3>{s.body}</div>
          </li>
        ))}
      </ol>
    </section>
  );
}
