import { Routes, Route } from "react-router-dom";
import Home from "./pages/Home";
import ApiDetail from "./pages/ApiDetail";
import Consumers from "./pages/Consumers";
import ConsumerDetail from "./pages/ConsumerDetail";
import GettingStarted from "./pages/GettingStarted";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Profile from './pages/Profile'
import McpDetail from "./pages/McpDetail";
import Agent from "./pages/Agent";
import AgentDetail from "./pages/AgentDetail";
import ModelDetail from "./pages/ModelDetail";
import Callback from "./pages/Callback";
import OidcCallback from "./pages/OidcCallback";
import Square from "./pages/Square";
import Chat from "./pages/Chat";

export function Router() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/models" element={<Square activeType="MODEL_API" />} />
      <Route path="/mcp" element={<Square activeType="MCP_SERVER" />} />
      <Route path="/agents" element={<Square activeType="AGENT_API" />} />
      <Route path="/apis" element={<Square activeType="REST_API" />} />
      <Route path="/chat" element={<Chat />} />
      <Route path="/getting-started" element={<GettingStarted />} />
      <Route path="/apis/:apiProductId" element={<ApiDetail />} />
      <Route path="/consumers/:consumerId" element={<ConsumerDetail />} />
      <Route path="/consumers" element={<Consumers />} />
      <Route path="/mcp/:mcpProductId" element={<McpDetail />} />
      <Route path="/agents" element={<Agent />} />
      <Route path="/agents/:agentProductId" element={<AgentDetail />} />
      <Route path="/models/:modelProductId" element={<ModelDetail />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="/callback" element={<Callback />} />
      <Route path="/oidc/callback" element={<OidcCallback />} />

      {/* 其他页面可继续添加 */}
    </Routes>
  );
} 