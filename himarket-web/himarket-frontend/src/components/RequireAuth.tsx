import { Navigate, useLocation } from 'react-router-dom';

import type { ReactNode } from 'react';

interface RequireAuthProps {
  children: ReactNode;
}

export function RequireAuth({ children }: RequireAuthProps) {
  const location = useLocation();
  const isLoggedIn = !!localStorage.getItem('access_token');

  if (!isLoggedIn) {
    const returnUrl = encodeURIComponent(location.pathname + location.search);
    return <Navigate replace to={`/login?returnUrl=${returnUrl}`} />;
  }

  return <>{children}</>;
}
