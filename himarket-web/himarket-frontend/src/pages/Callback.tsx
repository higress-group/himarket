import { message, Spin } from 'antd';
import React, { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';

import request from '../lib/request';

const Callback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation('common');

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    const code = searchParams.get('code');
    const state = searchParams.get('state');

    if (!code || !state) {
      message.error(t('authCallback.missingParams'));
      return;
    }

    request
      .post<{ access_token: string }>('/developers/token', { code, state })
      .then((res) => {
        if (res && res.data && res.data.access_token) {
          message.success(t('authCallback.loginSuccess'));
          localStorage.setItem('access_token', res.data.access_token);
          navigate('/');
        } else {
          message.error(t('authCallback.noAccessToken'));
        }
      })
      .catch(() => {
        message.error(t('authCallback.loginFailed'));
      });
  }, [location.search, navigate, t]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <Spin tip={t('authCallback.loggingIn')} />
    </div>
  );
};

export default Callback;
