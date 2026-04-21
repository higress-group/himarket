import { createBrowserRouter, Navigate } from 'react-router-dom';

import LayoutWrapper from '@/components/LayoutWrapper';
import ApiProductDetail from '@/pages/ApiProductDetail';
import GatewayConsoles from '@/pages/GatewayConsoles';
import Login from '@/pages/Login';
import McpMonitor from '@/pages/McpMonitor';
import ModelDashboard from '@/pages/ModelDashboard';
import NacosConsoles from '@/pages/NacosConsoles';
import PortalDetail from '@/pages/PortalDetail';
import Portals from '@/pages/Portals';
import ProductCategories from '@/pages/ProductCategories';
import ProductCategoryDetail from '@/pages/ProductCategoryDetail';
import ProductTypePage from '@/pages/ProductTypePage';
import SandboxConsoles from '@/pages/SandboxConsoles';

export const router = createBrowserRouter([
  {
    element: <Login />,
    path: '/login',
  },
  {
    children: [
      {
        element: <Navigate replace to="/portals" />,
        index: true,
      },
      {
        element: <Portals />,
        path: 'portals',
      },
      {
        element: <PortalDetail />,
        path: 'portals/:portalId',
      },
      {
        children: [
          {
            element: <Navigate replace to="/api-products/model-api" />,
            index: true,
          },
          {
            element: <ProductTypePage productType="MODEL_API" />,
            path: 'model-api',
          },
          {
            element: <ProductTypePage productType="MCP_SERVER" />,
            path: 'mcp-server',
          },
          {
            element: <ProductTypePage productType="AGENT_SKILL" />,
            path: 'agent-skill',
          },
          {
            element: <ProductTypePage productType="WORKER" />,
            path: 'worker',
          },
          {
            element: <ProductTypePage productType="AGENT_API" />,
            path: 'agent-api',
          },
          {
            element: <ProductTypePage productType="REST_API" />,
            path: 'rest-api',
          },
          {
            element: <ApiProductDetail />,
            path: ':productId',
          },
        ],
        path: 'api-products',
      },
      {
        element: <ProductCategories />,
        path: 'product-categories',
      },
      {
        element: <ProductCategoryDetail />,
        path: 'product-categories/:categoryId',
      },
      {
        element: <Navigate replace to="/consoles/gateway" />,
        path: 'consoles',
      },
      {
        element: <GatewayConsoles />,
        path: 'consoles/gateway',
      },
      {
        element: <NacosConsoles />,
        path: 'consoles/nacos',
      },
      {
        element: <SandboxConsoles />,
        path: 'consoles/sandbox',
      },
      {
        element: <Navigate replace to="/observability/model-dashboard" />,
        path: 'observability',
      },
      {
        element: <ModelDashboard />,
        path: 'observability/model-dashboard',
      },
      {
        element: <McpMonitor />,
        path: 'observability/mcp-monitor',
      },
      {
        element: <Navigate replace to="/portals" />,
        path: '*',
      },
    ],
    element: <LayoutWrapper />,
    path: '/',
  },
]);
