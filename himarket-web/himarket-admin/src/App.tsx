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
        }}
      >
        <RouterProvider router={router} />
      </ConfigProvider>
    </LoadingProvider>
  )
}

export default App
