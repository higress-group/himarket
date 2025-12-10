import React, { useEffect, useState } from 'react'
// import { useLocation } from 'react-router-dom'

import aliyunIcon from '../assets/aliyun.png';
import githubIcon from '../assets/github.png';
import googleIcon from '../assets/google.png';
import { message } from "antd";
import APIs, { type IIdentity, type IIdpProvider } from '../lib/apis';

const providerIcons: Record<string, string> = {
  aliyun: aliyunIcon,
  github: githubIcon,
  google: googleIcon,
}

interface UserProfile {
  avatar?: string;
  name?: string;
  email?: string;
  provider?: string;
}

const parseUserProfile = (identity: IIdentity): UserProfile => {
  if (!identity) return {};
  let raw: Record<string, unknown> = {};
  try {
    raw = JSON.parse(identity.rawInfoJson);
  } catch {
    // 忽略解析错误
  }
  // 针对不同provider做兼容
  if (identity.provider === 'github') {
    return {
      avatar: raw.avatar_url as string,
      name: raw.name as string || raw.login as string,
      email: raw.email as string,
      provider: 'github',
    };
  } else if (identity.provider === 'google') {
    return {
      avatar: raw.picture as string,
      name: raw.name as string,
      email: raw.email as string,
      provider: 'google',
    };
  } else if (identity.provider === 'aliyun') {
    return {
      avatar: raw.avatar as string,
      name: raw.name as string,
      email: raw.email as string,
      provider: 'aliyun',
    };
  }
  return {};
};

const Profile: React.FC = () => {
  const [providers, setProviders] = useState<IIdpProvider[]>([])
  const [identities, setIdentities] = useState<IIdentity[]>([])

  useEffect(() => {
    // 使用OidcController的接口获取OIDC提供商
    APIs.getOidcProviders()
      .then((response) => {
        console.log('OIDC providers response:', response);

        // 处理不同的响应格式
        let providersData: IIdpProvider[];
        if (Array.isArray(response)) {
          providersData = response;
        } else if (response && Array.isArray(response.data)) {
          providersData = response.data;
        } else if (response && response.data) {
          console.warn('Unexpected response format:', response);
          providersData = [];
        } else {
          providersData = [];
        }

        console.log('Processed providers data:', providersData);
        setProviders(providersData);
      })
      .catch((error) => {
        console.error('Failed to fetch OIDC providers:', error);
        setProviders([]);
      });
  }, [])

  useEffect(() => {
    APIs.developersListIdentities()
      .then((res) => {
        setIdentities(res.data || [])
      })
      .catch(() => setIdentities([]))
  }, [])

  // OIDC绑定功能 - 暂时简化实现
  const handleBinding = (provider: string) => {
    // 由于简化了OIDC流程，绑定功能需要单独实现
    // 暂时提示用户功能开发中
    message.info(`${provider} 账号绑定功能开发中，敬请期待`);

    // 后续可以考虑以下实现方案：
    // 1. 为绑定功能创建专门的回调页面
    // 2. 通过URL参数区分登录和绑定模式
    // 3. 或者使用弹窗方式处理绑定流程
  }

  // 判断provider是否已绑定
  const isBound = (provider: string) =>
    identities.some((id) => id.provider === provider)

  // 取第一个已绑定身份展示个人信息
  const mainIdentity = identities[0];
  const userProfile = mainIdentity ? parseUserProfile(mainIdentity) : null;

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-white">
      <div className="bg-white p-8 rounded-xl shadow-2xl w-full max-w-md flex flex-col items-center border border-gray-100">
        <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">个人中心</h2>
        {/* 个人信息展示区 */}
        {userProfile && (
          <div className="flex flex-col items-center mb-8">
            {userProfile.avatar && <img src={userProfile.avatar} alt="avatar" className="w-16 h-16 rounded-full mb-2" />}
            <div className="text-lg font-semibold text-gray-900">{userProfile.name}</div>
            {userProfile.email && <div className="text-gray-500 text-sm">{userProfile.email}</div>}
            {userProfile.provider && <div className="text-gray-400 text-xs mt-1">来自 {userProfile.provider} 账号</div>}
          </div>
        )}
        <div className="w-full flex flex-col gap-3 mb-6">
          {!Array.isArray(providers) || providers.length === 0 ? (
            <div className="text-gray-400 text-center">暂无可用第三方</div>
          ) : (
            providers.map((provider) => {
              const bound = isBound(provider.provider)
              const icon = providerIcons[provider.provider] || ''
              return (
                <div
                  key={provider.provider}
                  className={`w-full flex items-center gap-2 py-2 rounded border text-base font-medium shadow-sm px-3 ${bound ? 'bg-green-50 border-green-200 text-green-700' : 'bg-white border-gray-200 text-gray-900'}`}
                >
                  {icon && <img src={icon} alt={provider.provider} className="w-6 h-6" />}
                  <span className="flex-1">{provider.name || provider.provider}</span>
                  {bound ? (
                    <span className="px-2 py-1 rounded bg-green-100 text-green-700 text-xs font-semibold">已绑定</span>
                  ) : (
                    <button
                      onClick={() => handleBinding(provider.provider)}
                      className="px-3 py-1 rounded bg-blue-500 text-white hover:bg-blue-600 text-xs font-semibold transition-colors"
                    >
                      绑定
                    </button>
                  )}
                </div>
              )
            })
          )}
        </div>
      </div>
    </div>
  )
}

export default Profile 