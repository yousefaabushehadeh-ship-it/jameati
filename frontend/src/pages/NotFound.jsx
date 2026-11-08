import { Link } from 'react-router-dom';
export default function NotFound() {
  return (
    <div className="container section empty">
      <h1>الصفحة مش موجودة</h1>
      <Link to="/" className="btn btn-primary">رجوع للرئيسية</Link>
    </div>
  );
}
