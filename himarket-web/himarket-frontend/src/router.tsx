import { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';

import { RequireAuth } from './components/RequireAuth';
import { usePortalConfig } from './context/usePortalConfig';
import AgentDetail from './pages/AgentDetail';
import ApiDetail from './pages/ApiDetail';
import Callback from './pages/Callback';
import Chat from './pages/Chat';
import Coding from './pages/Coding';
import ConsumerDetail from './pages/ConsumerDetail';
import Consumers from './pages/Consumers';
import GettingStarted from './pages/GettingStarted';
import Login from './pages/Login';
import McpCreatePage from './pages/McpCreatePage';
import McpDetail from './pages/McpDetail';
import McpSquare from './pages/McpSquare';
import ModelDetail from './pages/ModelDetail';
import MyMcp from './pages/MyMcp';
import OidcCallback from './pages/OidcCallback';
import Profile from './pages/Profile';
import Register from './pages/Register';
import SkillDetail from './pages/SkillDetail';
import Square from './pages/Square';
import WorkerDetail from './pages/WorkerDetail';

function DynamicHome() {
  const { firstVisiblePath } = usePortalConfig();
  return <Navigate replace to={firstVisiblePath} />;
}

function MenuRedirectGuard() {
  const location = useLocation();
  const navigate = useNavigate();
  const { firstVisiblePath, isMenuVisible, loading } = usePortalConfig();

  useEffect(() => {
    if (loading) return;

    const pathToKeyMap: Record<string, string> = {
      '/agents': 'agents',
      '/apis': 'apis',
      '/chat': 'chat',
      '/coding': 'coding',
      '/mcp': 'mcp',
      '/models': 'models',
      '/skills': 'skills',
      '/workers': 'workers',
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
        <Route element={<DynamicHome />} path="/" />
        <Route element={<Square activeType="MODEL_API" />} path="/models" />
        <Route element={<McpSquare />} path="/mcp" />
        <Route
          element={
            <RequireAuth>
              <MyMcp />
            </RequireAuth>
          }
          path="/mcp/my"
        />
        <Route
          element={
            <RequireAuth>
              <McpCreatePage />
            </RequireAuth>
          }
          path="/mcp/create"
        />
        <Route element={<Square activeType="AGENT_API" />} path="/agents" />
        <Route element={<Square activeType="REST_API" />} path="/apis" />
        <Route element={<Square activeType="AGENT_SKILL" />} path="/skills" />
        <Route element={<SkillDetail />} path="/skills/:skillProductId" />
        <Route element={<Square activeType="WORKER" />} path="/workers" />
        <Route element={<WorkerDetail />} path="/workers/:workerProductId" />
        <Route element={<Chat />} path="/chat" />
        <Route element={<Navigate to="/coding" />} path="/quest" />
        <Route element={<Coding />} path="/coding" />
        <Route element={<GettingStarted />} path="/getting-started" />
        <Route element={<ApiDetail />} path="/apis/:apiProductId" />
        <Route
          element={
            <RequireAuth>
              <ConsumerDetail />
            </RequireAuth>
          }
          path="/consumers/:consumerId"
        />
        <Route
          element={
            <RequireAuth>
              <Consumers />
            </RequireAuth>
          }
          path="/consumers"
        />
        <Route element={<McpDetail />} path="/mcp/:mcpProductId" />
        <Route element={<AgentDetail />} path="/agents/:agentProductId" />
        <Route element={<ModelDetail />} path="/models/:modelProductId" />
        <Route element={<Login />} path="/login" />
        <Route element={<Register />} path="/register" />
        <Route
          element={
            <RequireAuth>
              <Profile />
            </RequireAuth>
          }
          path="/profile"
        />
        <Route element={<Callback />} path="/callback" />
        <Route element={<OidcCallback />} path="/oidc/callback" />

        {/* 其他页面可继续添加 */}
      </Routes>
    </>
  );
}
