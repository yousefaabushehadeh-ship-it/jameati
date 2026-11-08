import { useState } from 'react';
import { get, post } from '../lib/api';
import { useFetch } from '../lib/useFetch';

export default function AiChat({ path }) {
  const status = useFetch(() => get('/public/ai/status'));
  const [history, setHistory] = useState([]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  if (!status.data?.enabled) return null;

  const send = async (e) => {
    e.preventDefault();
    const message = text.trim();
    if (!message) return;
    setBusy(true); setErr('');
    try {
      const r = await post(path, { message, history: history.slice(-8) });
      setHistory((h) => [...h, { role: 'user', content: message }, { role: 'assistant', content: r.answer }]);
      setText('');
    } catch (e2) { setErr(e2.message); }
    setBusy(false);
  };

  return (
    <div className="card ai">
      <h3>🤖 اسأل المساعد عن هالفرصة</h3>
      <div className="ai-log">
        {history.map((m, i) => <div key={i} className={`ai-msg ai-${m.role}`}>{m.content}</div>)}
      </div>
      <form onSubmit={send} className="row">
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="مثلاً: شو الشروط المطلوبة؟" maxLength={1000} />
        <button className="btn btn-primary" disabled={busy}>{busy ? '…' : 'إرسال'}</button>
      </form>
      {err && <p className="error small">{err}</p>}
    </div>
  );
}
