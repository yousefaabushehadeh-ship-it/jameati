export const FUNDING = {
  FULLY_FUNDED: 'ممولة بالكامل',
  PARTIALLY_FUNDED: 'ممولة جزئيًا',
  SELF_FUNDED: 'ذاتية التمويل',
  FREE: 'مجانية',
  PAID: 'مدفوعة',
};

export const STATUS = {
  OPEN: { label: 'مفتوحة', tone: 'green' },
  UPCOMING: { label: 'قريبًا', tone: 'blue' },
  CLOSING_SOON: { label: 'تغلق قريبًا', tone: 'orange' },
  EXPIRED: { label: 'مغلقة', tone: 'red' },
  ALWAYS_OPEN: { label: 'مفتوحة دائمًا', tone: 'green' },
  UNKNOWN: { label: 'غير محدد', tone: 'gray' },
};

export const APP_STATUS = {
  PLANNING: 'أخطط للتقديم',
  IN_PROGRESS: 'قيد التحضير',
  SUBMITTED: 'تم التقديم',
  INTERVIEW: 'مقابلة',
  ACCEPTED: 'مقبول',
  REJECTED: 'مرفوض',
  WITHDRAWN: 'منسحب',
};

// كل قسم من الكتالوج: مساره بالموقع، عناوينه، أيقونته، والتصنيفات اللي بتظهر كفلاتر
export const CATALOG = {
  COURSE: {
    path: '/courses', title: 'الدورات والكورسات', nav: 'الدورات', single: 'دورة', unit: 'دورة', icon: 'book',
    subtitle: 'طوّر مهاراتك بدورات مرنة وشهادات عملية', search: 'ابحث عن دورة...',
    eyebrow: 'الدورات والكورسات', homeTitle: 'تعلّم شيئًا يفتح لك بابًا جديدًا', homeSub: 'اختيارات عملية تساعدك على بناء مهارة قابلة للاستخدام.', more: 'استكشف كل الدورات',
    categories: ['تطوير مهني', 'تقنية وبرمجة', 'لغات', 'أعمال وإدارة', 'تصميم وإبداع'],
  },
  ARTICLE: {
    path: '/articles', title: 'المقالات والأدلة', nav: 'المقالات', single: 'مقال', unit: 'مقال', icon: 'file',
    subtitle: 'أدلة ومقالات تساعدك تاخذ قرارك بثقة', search: 'ابحث عن مقال...', categories: [], sorts: true,
  },
  JOB: {
    path: '/jobs', title: 'الوظائف', nav: 'الوظائف', single: 'وظيفة', unit: 'وظيفة', icon: 'briefcase',
    subtitle: 'فرص عمل وتدريب من جهات موثوقة', search: 'ابحث عن وظيفة...', categories: ['تقنية', 'إدارة'],
  },
  UNIVERSITY: {
    path: '/universities', title: 'دليل الجامعات', nav: 'الجامعات', single: 'جامعة', unit: 'جامعة', icon: 'building',
    subtitle: 'قارن البرامج والجامعات وابحث عن وجهتك', search: 'ابحث عن جامعة...',
    eyebrow: 'دليل الجامعات', homeTitle: 'جامعات تستحق أن تعرفها', homeSub: 'قارن الوجهات والبرامج قبل أن تختار جامعتك.', more: 'استكشف كل الجامعات',
    categories: ['جامعة حكومية', 'جامعة خاصة', 'جامعة ذات قانون خاص', 'جامعة إقليمية', 'جامعة أو مؤسسة تعليم عالٍ'],
  },
  ORGANIZATION: {
    path: '/organizations', title: 'المؤسسات والجهات المانحة', nav: 'المؤسسات', single: 'مؤسسة', unit: 'مؤسسة', icon: 'building',
    subtitle: 'تابع الجهات الموثوقة وفرصها الجديدة', search: 'ابحث عن مؤسسة...',
    categories: ['مؤسسة غير ربحية', 'جهة حكومية', 'شركة', 'مؤسسة بحثية', 'مؤسسة صحية', 'مؤسسة معرفية', 'مؤسسة دولية'],
  },
  SPECIALITY: {
    path: '/specialities', title: 'دليل التخصصات', nav: 'التخصصات', single: 'تخصص', unit: 'تخصص', icon: 'cap',
    subtitle: 'افهم ما ستدرسه وأين يمكن أن يقودك', search: 'ابحث عن تخصص...',
    eyebrow: 'دليل التخصصات', homeTitle: 'اختر تخصصك على معرفة', homeSub: 'افهم طبيعة التخصص وآفاقه قبل اتخاذ القرار.', more: 'استكشف كل التخصصات',
    categories: ['العلوم الفيزيائية', 'العلوم الاجتماعية', 'العلوم الصحية', 'علوم الحياة'],
  },
};

export const typeByPath = Object.fromEntries(Object.entries(CATALOG).map(([k, v]) => [v.path.slice(1), k]));

export const num = (n) => (n ?? 0).toLocaleString('ar-EG');

export function date(d) {
  if (!d) return '';
  try {
    return new Date(d).toLocaleDateString('ar', { year: 'numeric', month: 'long', day: 'numeric' });
  } catch {
    return d;
  }
}

export const isClosed = (o) => o?.status === 'EXPIRED';

export function deadlineText(o) {
  if (o.alwaysOpen) return 'مفتوحة دائمًا';
  if (isClosed(o)) return `انتهى التقديم${o.deadline ? ` (${date(o.deadline)})` : ''}`;
  if (o.deadline) return date(o.deadline);
  return o.deadlineText || 'غير محدد';
}
