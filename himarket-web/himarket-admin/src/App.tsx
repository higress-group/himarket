import { RouterProvider } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import { router } from './routes'
import aliyunThemeToken from './aliyunThemeToken'
import { LoadingProvider } from './contexts/LoadingContext'
import './App.css'

function App() {
  return (
    <LoadingProvider>
      <ConfigProvider
        locale={zhCN}
        theme={{
          token: aliyunThemeToken,
          components: {
            Card: {
              borderRadiusLG: 12,
            },
            Table: {
              rowHoverBg: '#EEF2FF',
            },
            Button: {
              primaryShadow: '0 2px 4px rgba(99, 102, 241, 0.3)',
            },
          },
        }}
      >
        <RouterProvider router={router} />
      </ConfigProvider>
    </LoadingProvider>
  )
}

export default App
