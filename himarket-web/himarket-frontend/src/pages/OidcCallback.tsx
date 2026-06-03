import { message, Spin } from 'antd';
import React, { useEffect, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';

import { handleOidcCallback } from '../lib/apis';

const OidcCallback: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation('common');
  const [, setLoading] = useState(true);
  const processedRef = useRef(false);

  useEffect(() => {
    if (!processedRef.current) {
      processedRef.current = true;
      handleOidcCallbackProcess();
    }
  }, [location.search]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleOidcCallbackProcess = async () => {
    try {
      setLoading(true);

      const searchParams = new URLSearchParams(location.search);
      const code = searchParams.get('code');
      const state = searchParams.get('state');
      const error = searchParams.get('error');
      const errorDescription = searchParams.get('error_description');

      if (error) {
        message.error(
          t('authCallback.loginFailedWithReason', { reason: errorDescription || error }),
        );
        navigate('/login', { replace: true });
        return;
      }

      if (!code || !state) {
        message.error(t('authCallback.incompleteParams'));
        navigate('/login', { replace: true });
        return;
      }

      const authResult = await handleOidcCallback({ code, state });
      if (!authResult?.data?.access_token) {
        throw new Error(t('authCallback.noAccessToken'));
      }

      localStorage.setItem('access_token', authResult.data.access_token);

      message.success(t('authCallback.loginSuccess'));

      navigate('/', { replace: true });
    } catch (error) {
      let errorMessage = t('authCallback.loginFailed');
      if (error && typeof error === 'object' && 'response' in error) {
        const axiosError = error as { response?: { status: number } };
        if (axiosError.response?.status === 400) {
          errorMessage = t('authCallback.invalidCode');
        } else if (axiosError.response?.status === 404) {
          errorMessage = t('authCallback.oidcConfigMissing');
        }
      } else if (error instanceof Error && error.message) {
        errorMessage = error.message;
      }

      message.error(errorMessage);
      navigate('/login', { replace: true });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="text-center">
        <Spin size="large" />
        <div className="mt-4 text-gray-600">{t('authCallback.processing')}</div>
      </div>
    </div>
  );
};

export default OidcCallback;
