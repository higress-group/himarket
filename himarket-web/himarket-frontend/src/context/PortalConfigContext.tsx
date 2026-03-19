import { createContext, useContext, useState, useEffect, useMemo, type ReactNode } from "react";
import { getPortalUiConfig } from "../lib/apis/portal";

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
];

interface PortalConfigContextValue {
  isMenuVisible: (key: string) => boolean;
  visibleTabs: TabItem[];
  firstVisiblePath: string;
  loading: boolean;
}

const PortalConfigContext = createContext<PortalConfigContextValue>({
  isMenuVisible: () => true,
  visibleTabs: ALL_TABS,
  firstVisiblePath: "/models",
  loading: true,
});

export function usePortalConfig() {
  return useContext(PortalConfigContext);
}

export function PortalConfigProvider({ children }: { children: ReactNode }) {
  const [menuVisibility, setMenuVisibility] = useState<Record<string, boolean> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getPortalUiConfig()
      .then((res) => {
        console.log("[PortalConfig] API response:", JSON.stringify(res));
        const mv = res.data?.menuVisibility ?? null;
        console.log("[PortalConfig] menuVisibility:", JSON.stringify(mv));
        setMenuVisibility(mv);
      })
      .catch((err) => {
        // 接口失败时静默降级，全部菜单显示
        console.warn("[PortalConfig] API failed:", err);
        setMenuVisibility(null);
      })
      .finally(() => {
        setLoading(false);
      });
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
    <PortalConfigContext.Provider value={{ isMenuVisible, visibleTabs, firstVisiblePath, loading }}>
      {children}
    </PortalConfigContext.Provider>
  );
}
