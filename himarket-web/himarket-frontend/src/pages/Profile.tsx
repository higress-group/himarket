import {
  CalendarOutlined,
  IdcardOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Skeleton, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

import { ChangePasswordForm } from '../components/ChangePasswordForm';
import { Layout } from '../components/Layout';
import { notifyAuthInvalidated } from '../hooks/useAuth';
import APIs, { type IDeveloperInfo } from '../lib/apis';
import { clearCachedUserInfo } from '../lib/userInfoCache';
import { formatDateTime } from '../lib/utils';

type ProfileSection = 'identity' | 'profile' | 'security';

const getInitials = (name: string) => {
  if (!name) return 'U';
  if (/[\u4e00-\u9fa5]/.test(name)) {
    return name.charAt(0);
  }
  return name.charAt(0).toUpperCase();
};

const Profile: React.FC = () => {
  const { t } = useTranslation('profile');
  const navigate = useNavigate();
  const [activeSection, setActiveSection] = useState<ProfileSection>('profile');
  const [developerInfo, setDeveloperInfo] = useState<IDeveloperInfo | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [changePasswordLoading, setChangePasswordLoading] = useState(false);

  useEffect(() => {
    APIs.getDeveloperInfo()
      .then((response) => {
        setDeveloperInfo(response.data || null);
      })
      .catch(() => {
        setDeveloperInfo(null);
      })
      .finally(() => {
        setProfileLoading(false);
      });
  }, []);

  const handleChangePassword = async (values: { newPassword: string; oldPassword: string }) => {
    setChangePasswordLoading(true);
    try {
      await APIs.changeDeveloperPassword(values);
      message.success(t('changePasswordSuccess'), 1);
      localStorage.removeItem('access_token');
      clearCachedUserInfo();
      notifyAuthInvalidated();
      navigate('/login');
    } finally {
      setChangePasswordLoading(false);
    }
  };

  const displayName = developerInfo?.username || developerInfo?.email || t('unknownDeveloper');
  const displayEmail = developerInfo?.email;
  const avatar = developerInfo?.avatarUrl;
  const loadingProfile = profileLoading;

  const navItems = [
    { icon: <UserOutlined />, key: 'profile' as const, label: t('profileInfo') },
    { icon: <SafetyCertificateOutlined />, key: 'security' as const, label: t('accountSecurity') },
    {
      comingSoon: true,
      icon: <IdcardOutlined />,
      key: 'identity' as const,
      label: t('thirdPartyAccounts'),
    },
  ];

  const profileFacts = [
    {
      icon: <UserOutlined />,
      label: t('username'),
      value: developerInfo?.username || t('notSet'),
    },
    {
      icon: <MailOutlined />,
      label: t('email'),
      value: displayEmail || t('notSet'),
    },
    {
      icon: <CalendarOutlined />,
      label: t('joinedAt'),
      value: developerInfo?.createAt ? formatDateTime(developerInfo.createAt) : '-',
    },
  ];

  const renderProfileContent = () => (
    <>
      <div className="border-b border-gray-200 pb-4">
        <h1 className="m-0 text-lg font-medium text-gray-800">{t('profileInfo')}</h1>
        <p className="mt-2 text-sm leading-6 text-gray-500">{t('profileInfoDescription')}</p>
      </div>

      {loadingProfile ? (
        <Skeleton active className="mt-6" paragraph={{ rows: 6 }} />
      ) : (
        <dl className="mt-5 grid gap-3 md:grid-cols-2">
          {profileFacts.map((item) => (
            <div
              className="min-w-0 rounded-[10px] border border-gray-200 bg-white px-4 py-3 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
              key={item.label}
            >
              <dt className="flex items-center gap-2 text-xs font-medium text-gray-500">
                <span className="text-sm text-gray-400">{item.icon}</span>
                <span>{item.label}</span>
              </dt>
              <dd className="mt-2 truncate text-sm font-normal text-gray-700">{item.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </>
  );

  const renderSecurityContent = () => (
    <>
      <div className="border-b border-gray-200 pb-4">
        <h1 className="m-0 text-lg font-medium text-gray-800">{t('accountSecurity')}</h1>
        <p className="mt-2 text-sm leading-6 text-gray-500">{t('passwordDescription')}</p>
      </div>

      <ChangePasswordForm loading={changePasswordLoading} onSubmit={handleChangePassword} />
    </>
  );

  const renderIdentityContent = () => (
    <>
      <div className="border-b border-gray-200 pb-4">
        <h1 className="m-0 text-lg font-medium text-gray-800">{t('thirdPartyAccounts')}</h1>
        <p className="mt-2 text-sm leading-6 text-gray-500">{t('thirdPartyDescription')}</p>
      </div>

      <div className="mt-6 rounded-[10px] border border-dashed border-gray-200 bg-white px-6 py-10 text-center">
        <div className="inline-flex rounded bg-gray-100 px-2 py-1 text-xs font-medium text-gray-500">
          {t('comingSoon')}
        </div>
      </div>
    </>
  );

  const renderActiveContent = () => {
    if (activeSection === 'security') return renderSecurityContent();
    if (activeSection === 'identity') return renderIdentityContent();
    return renderProfileContent();
  };

  return (
    <Layout>
      <div className="w-full">
        <div className="min-h-[calc(100vh-96px)] rounded-2xl border border-white/40 bg-white p-6 shadow-xs backdrop-blur-xl">
          <div className="grid w-full gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
            <aside className="min-w-0">
              {loadingProfile ? (
                <div>
                  <Skeleton.Avatar active size={64} />
                  <Skeleton active className="mt-4" paragraph={{ rows: 2 }} />
                </div>
              ) : (
                <div className="mb-6 flex items-center gap-3">
                  {avatar ? (
                    <img
                      alt={displayName}
                      className="h-12 w-12 rounded-full object-cover"
                      src={avatar}
                    />
                  ) : (
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-50 text-lg font-medium text-gray-500">
                      {getInitials(displayName)}
                    </div>
                  )}
                  <div className="min-w-0">
                    <div className="truncate text-base font-medium text-gray-800">
                      {displayName}
                    </div>
                    {displayEmail && (
                      <div className="mt-1 truncate text-xs text-gray-400">{displayEmail}</div>
                    )}
                  </div>
                </div>
              )}

              <nav className="space-y-1 border-b border-gray-200 pb-4">
                {navItems.map((item) => {
                  const isActive = activeSection === item.key;

                  return (
                    <button
                      className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium transition-colors ${
                        isActive
                          ? 'bg-indigo-50 text-indigo-700'
                          : 'text-gray-600 hover:bg-white hover:text-gray-950'
                      }`}
                      key={item.key}
                      onClick={() => setActiveSection(item.key)}
                      type="button"
                    >
                      <span
                        className={`text-base ${isActive ? 'text-indigo-600' : 'text-gray-500'}`}
                      >
                        {item.icon}
                      </span>
                      <span className="min-w-0 flex-1 truncate">{item.label}</span>
                      {item.comingSoon && (
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium text-gray-500">
                          {t('comingSoon')}
                        </span>
                      )}
                    </button>
                  );
                })}
              </nav>
            </aside>

            <main className="min-w-0 rounded-[10px] border border-gray-200 bg-white p-6 shadow-[0_1px_2px_rgba(15,23,42,0.04)]">
              {renderActiveContent()}
            </main>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Profile;
