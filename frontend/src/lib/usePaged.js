import { useEffect, useState } from 'react';
import { pageOf } from './api';

// تحميل صفحات متتالية وجمعها (زر "تحميل المزيد")
export function usePaged(fetchPage, deps) {
  const [state, setState] = useState({ items: [], page: 0, totalPages: 0, total: 0, loading: true, error: null });

  const load = (page, append) => {
    setState((s) => ({ ...s, loading: true, error: null }));
    return fetchPage(page)
      .then((raw) => {
        const p = pageOf(raw);
        setState((s) => ({ ...p, items: append ? [...s.items, ...p.items] : p.items, loading: false, error: null }));
      })
      .catch((error) => setState((s) => ({ ...s, loading: false, error })));
  };

  useEffect(() => {
    setState({ items: [], page: 0, totalPages: 0, total: 0, loading: true, error: null });
    load(0, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return {
    ...state,
    hasMore: state.page < state.totalPages - 1,
    more: () => load(state.page + 1, true),
    reload: () => load(0, false),
  };
}
