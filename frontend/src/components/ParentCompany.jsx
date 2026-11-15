import { Link } from 'react-router-dom';
import Icon from './Icon';

export const STEAM_URL = 'https://steam-village.web.app/';

export const FOUNDER = {
  name: 'المهندسة بنان البنّا',
  title: 'المؤسِّسة والمديرة التنفيذية لـ STEAM Village',
  photo: '/founder.jpg',
  quote: 'كل برنامج صممناه بدأ من مشكلة حقيقية في صف دراسي.',
  bio: [
    'مهندسة بخبرة تزيد عن 13 عامًا في الابتكار التعليمي.',
    'دبلوم عالٍ في الذكاء الاصطناعي التطبيقي من الجامعة الأردنية.',
    'مستشارة تحول رقمي وذكاء اصطناعي للمدارس الخاصة والمؤسسات غير الربحية.',
    'تقود المبادرة الوطنية «صُنّاع 360».',
  ],
};

/** بطاقة "جامعتي من مبادرات STEAM Village" مع المؤسِّسة */
export default function ParentCompany({ full = false }) {
  return (
    <div className="parent-card">
      <div className="parent-photo">
        <img src={FOUNDER.photo} alt={FOUNDER.name} width="320" height="384" loading="lazy" />
      </div>
      <div className="parent-copy">
        <span className="parent-eyebrow"><Icon name="sparkle" size={16} /> جامعتي إحدى مبادرات STEAM Village</span>
        <h2>من الفكرة إلى المشروع، ومن المدرسة إلى الجامعة</h2>
        <p>
          جامعتي برنامج الإرشاد الجامعي والمهني لطلاب المدارس، يقدّمه <strong>مركز خبراء التعليم المستقبلي — STEAM Village</strong> في
          الأردن، المتخصص في برامج العلوم والتكنولوجيا والهندسة والفنون والرياضيات للأعمار من 6 إلى 35 سنة.
        </p>
        <blockquote>«{FOUNDER.quote}»</blockquote>
        <div className="parent-founder">
          <strong>{FOUNDER.name}</strong>
          <span>{FOUNDER.title}</span>
        </div>
        {full && (
          <ul className="parent-bio">
            {FOUNDER.bio.map((b) => <li key={b}>{b}</li>)}
          </ul>
        )}
        <div className="parent-actions">
          <a href={STEAM_URL} target="_blank" rel="noopener noreferrer" className="btn btn-sky">
            زوروا موقع STEAM Village <Icon name="arrow" size={16} />
          </a>
          {!full && <Link to="/about" className="btn btn-white">من نحن</Link>}
        </div>
      </div>
    </div>
  );
}
