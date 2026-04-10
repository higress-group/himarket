import { createBrowserRouter, Navigate } from "react-router-dom";
import LayoutWrapper from "@/components/LayoutWrapper";
import Portals from "@/pages/Portals";
import ProductTypePage from "@/pages/ProductTypePage";
import ProductCategories from "@/pages/ProductCategories";
import ProductCategoryDetail from "@/pages/ProductCategoryDetail";
import GatewayConsoles from "@/pages/GatewayConsoles";
import NacosConsoles from "@/pages/NacosConsoles";
import PortalDetail from "@/pages/PortalDetail";
import ApiProductDetail from "@/pages/ApiProductDetail";
import Login from "@/pages/Login";
import ModelDashboard from "@/pages/ModelDashboard";
import McpMonitor from "@/pages/McpMonitor";
import SandboxConsoles from "@/pages/SandboxConsoles";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <Login />,
  },
  {
    path: "/",
    element: <LayoutWrapper />,
    children: [
      {
        index: true,
        element: <Navigate to="/portals" replace />,
      },
      {
        path: "portals",
        element: <Portals />,
      },
      {
        path: "portals/:portalId",
        element: <PortalDetail />,
      },
      {
        path: "api-products",
        children: [
          {
            index: true,
            element: <Navigate to="/api-products/model-api" replace />,
          },
          {
            path: "model-api",
            element: <ProductTypePage productType="MODEL_API" />,
          },
          {
            path: "mcp-server",
            element: <ProductTypePage productType="MCP_SERVER" />,
          },
          {
            path: "agent-skill",
            element: <ProductTypePage productType="AGENT_SKILL" />,
          },
          {
            path: "worker",
            element: <ProductTypePage productType="WORKER" />,
          },
          {
            path: "agent-api",
            element: <ProductTypePage productType="AGENT_API" />,
          },
          {
            path: "rest-api",
            element: <ProductTypePage productType="REST_API" />,
          },
          {
            path: ":productId",
            element: <ApiProductDetail />,
          },
        ],
      },
      {
        path: "product-categories",
        element: <ProductCategories />,
      },
      {
        path: "product-categories/:categoryId",
        element: <ProductCategoryDetail />,
      },
      {
        path: "consoles",
        element: <Navigate to="/consoles/gateway" replace />,
      },
      {
        path: "consoles/gateway",
        element: <GatewayConsoles />,
      },
      {
        path: "consoles/nacos",
        element: <NacosConsoles />,
      },
      {
        path: 'consoles/sandbox',
        element: <SandboxConsoles />,
      },
      {
        path: 'observability',
        element: <Navigate to="/observability/model-dashboard" replace />,
      },
      {
        path: "observability/model-dashboard",
        element: <ModelDashboard />,
      },
      {
        path: "observability/mcp-monitor",
        element: <McpMonitor />,
      },
      {
        path: "*",
        element: <Navigate to="/portals" replace />,
      },
    ],
  },
]);
