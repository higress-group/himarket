import { useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";

export function useAuth() {
  const navigate = useNavigate();

  const token = useMemo(() => localStorage.getItem("access_token"), []);
  const isLoggedIn = useMemo(() => !!token, [token]);

  const login = useCallback(
    (returnUrl?: string) => {
      const url =
        returnUrl || window.location.pathname + window.location.search;
      navigate(`/login?returnUrl=${encodeURIComponent(url)}`);
    },
    [navigate]
  );

  return { isLoggedIn, login, token };
}
