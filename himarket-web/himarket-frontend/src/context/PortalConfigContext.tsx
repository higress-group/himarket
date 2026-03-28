import { createContext, useContext, useState, useEffect, useMemo, type ReactNode } from "react";
import { getPortalProfile } from "../lib/apis/portal";

export interface TabItem {
  key: string;
  path: string;
  label: string;
}

const ALL_TABS: TabItem[] = [
  { key: "chat", path: "/chat", label: "HiChat" },
  { key: "coding", path: "/coding", label: "HiCoding" },
  { key: "agents", path: "/agents", label: "智能体" },
  { key: "mcp", path: "/mcp", label: "MCP" },
  { key: "models", path: "/models", label: "模型" },
  { key: "apis", path: "/apis", label: "API" },
  { key: "skills", path: "/skills", label: "Skills" },
  { key: "workers", path: "/workers", label: "Workers" },
];

interface PortalConfigContextValue {
  portalId: string;
  isMenuVisible: (key: string) => boolean;
  visibleTabs: TabItem[];
  firstVisiblePath: string;
  loading: boolean;
}

const PortalConfigContext = createContext<PortalConfigContextValue>({
  portalId: '',
  isMenuVisible: () => true,
  visibleTabs: ALL_TABS,
  firstVisiblePath: "/models",
  loading: true,
});

export function usePortalConfig() {
  return useContext(PortalConfigContext);
}

export function PortalConfigProvider({ children }: { children: ReactNode }) {
  const [portalId, setPortalId] = useState('');
  const [menuVisibility, setMenuVisibility] = useState<Record<string, boolean> | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchConfig = () => {
    getPortalProfile()
      .then((res) => {
        console.log("[PortalConfig] API response:", JSON.stringify(res));
        setPortalId(res.data?.portalId || '');
        const mv = res.data?.portalUiConfig?.menuVisibility ?? null;
        console.log("[PortalConfig] menuVisibility:", JSON.stringify(mv));
        setMenuVisibility(mv);
      })
      .catch((err) => {
        console.warn("[PortalConfig] API failed:", err);
        setMenuVisibility(null);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchConfig();
  }, []);

  const isMenuVisible = (key: string): boolean => {
    if (menuVisibility == null) return true;
    return menuVisibility[key] ?? true;
  };

  const visibleTabs = useMemo(() => {
    const result = ALL_TABS.filter((tab) => isMenuVisible(tab.key));
    console.log("[PortalConfig] visibleTabs:", result.map((t) => t.key));
    return result;
  }, [menuVisibility]);

  const firstVisiblePath = useMemo(
    () => (visibleTabs.length > 0 ? visibleTabs[0].path : "/models"),
    [visibleTabs]
  );

  return (
    <PortalConfigContext.Provider value={{ portalId, isMenuVisible, visibleTabs, firstVisiblePath, loading }}>
      {children}
    </PortalConfigContext.Provider>
  );
}
