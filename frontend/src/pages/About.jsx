import ParentCompany from '../components/ParentCompany';

export default function About() {
  return (
    <section className="container section">
      <h1 className="about-title">من نحن</h1>
      <p className="about-lead">
        جامعتي منصة عربية تجمع فرص التعلم والعمل وتساعد الطلاب يخططوا رحلتهم من الاكتشاف حتى القبول، وهي جزء من منظومة STEAM Village التعليمية.
      </p>
      <ParentCompany full />
    </section>
  );
}
