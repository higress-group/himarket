import { Routes, Route, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useEffect } from "react";
import ApiDetail from "./pages/ApiDetail";
import Consumers from "./pages/Consumers";
import ConsumerDetail from "./pages/ConsumerDetail";
import GettingStarted from "./pages/GettingStarted";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Profile from "./pages/Profile";
import McpDetail from "./pages/McpDetail";
import AgentDetail from "./pages/AgentDetail";
import ModelDetail from "./pages/ModelDetail";
import Callback from "./pages/Callback";
import OidcCallback from "./pages/OidcCallback";
import Square from "./pages/Square";
import McpSquare from "./pages/McpSquare";
import MyMcp from "./pages/MyMcp";
import McpCreatePage from "./pages/McpCreatePage";
import Chat from "./pages/Chat";
import Coding from "./pages/Coding";
import SkillDetail from "./pages/SkillDetail";
import WorkerDetail from "./pages/WorkerDetail";
import { RequireAuth } from "./components/RequireAuth";
import { usePortalConfig } from "./context/PortalConfigContext";

function DynamicHome() {
  const { firstVisiblePath } = usePortalConfig();
  return <Navigate to={firstVisiblePath} replace />;
}

function MenuRedirectGuard() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isMenuVisible, firstVisiblePath, loading } = usePortalConfig();

  useEffect(() => {
    if (loading) return;

    const pathToKeyMap: Record<string, string> = {
      "/chat": "chat",
      "/coding": "coding",
      "/agents": "agents",
      "/mcp": "mcp",
      "/models": "models",
      "/apis": "apis",
      "/skills": "skills",
      "/workers": "workers",
    };

    const currentPath = location.pathname;
    // 仅拦截顶级菜单路径，不拦截子路径（如 /models/xxx）
    const menuKey = pathToKeyMap[currentPath];
    if (menuKey && !isMenuVisible(menuKey)) {
      navigate(firstVisiblePath, { replace: true });
    }
  }, [location.pathname, isMenuVisible, firstVisiblePath, loading, navigate]);

  return null;
}

export function Router() {
  return (
    <>
      <MenuRedirectGuard />
      <Routes>
        <Route path="/" element={<DynamicHome />} />
        <Route path="/models" element={<Square activeType="MODEL_API" />} />
        <Route path="/mcp" element={<McpSquare />} />
        <Route path="/mcp/my" element={<RequireAuth><MyMcp /></RequireAuth>} />
        <Route path="/mcp/create" element={<RequireAuth><McpCreatePage /></RequireAuth>} />
        <Route path="/agents" element={<Square activeType="AGENT_API" />} />
        <Route path="/apis" element={<Square activeType="REST_API" />} />
        <Route path="/skills" element={<Square activeType="AGENT_SKILL" />} />
        <Route path="/skills/:skillProductId" element={<SkillDetail />} />
        <Route path="/workers" element={<Square activeType="WORKER" />} />
        <Route path="/workers/:workerProductId" element={<WorkerDetail />} />
        <Route path="/chat" element={<Chat />} />
        <Route path="/quest" element={<Navigate to="/coding" />} />
        <Route path="/coding" element={<Coding />} />
        <Route path="/getting-started" element={<GettingStarted />} />
        <Route path="/apis/:apiProductId" element={<ApiDetail />} />
        <Route path="/consumers/:consumerId" element={<RequireAuth><ConsumerDetail /></RequireAuth>} />
        <Route path="/consumers" element={<RequireAuth><Consumers /></RequireAuth>} />
        <Route path="/mcp/:mcpProductId" element={<McpDetail />} />
        <Route path="/agents/:agentProductId" element={<AgentDetail />} />
        <Route path="/models/:modelProductId" element={<ModelDetail />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="/callback" element={<Callback />} />
        <Route path="/oidc/callback" element={<OidcCallback />} />

        {/* 其他页面可继续添加 */}
      </Routes>
    </>
  );
}
