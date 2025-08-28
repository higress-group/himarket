import { createBrowserRouter, Navigate } from 'react-router-dom';
import LayoutWrapper from '@/components/LayoutWrapper';
import Portals from '@/pages/Portals';
import ApiProducts from '@/pages/ApiProducts';
import GatewayConsoles from '@/pages/GatewayConsoles';
import NacosConsoles from '@/pages/NacosConsoles';
import PortalDetail from '@/pages/PortalDetail';
import ApiProductDetail from '@/pages/ApiProductDetail';
import Login from '@/pages/Login';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/',
    element: <LayoutWrapper />,
    children: [
      {
        index: true,
        element: <Navigate to="/portals" replace />,
      },
      {
        path: 'portals',
        element: <Portals />,
      },
      {
        path: 'portals/detail',
        element: <PortalDetail />,
      },
      {
        path: 'api-products',
        element: <ApiProducts />,
      },
      {
        path: 'api-products/detail',
        element: <ApiProductDetail />,
      },
      {
        path: 'consoles',
        element: <Navigate to="/consoles/gateway" replace />,
      },
      {
        path: 'consoles/gateway',
        element: <GatewayConsoles />,
      },
      {
        path: 'consoles/nacos',
        element: <NacosConsoles />,
      },
      {
        path: '*',
        element: <Navigate to="/portals" replace />,
      },
    ],
  },
]);
