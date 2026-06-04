import { ArrowLeftOutlined } from '@ant-design/icons';
import { Alert, Tabs } from 'antd';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';

import { AuthConfig, SubscriptionManager } from '../components/consumer';
import { Layout } from '../components/Layout';
import APIs, { type IConsumer, type ISubscription } from '../lib/apis';
import request from '../lib/request';

import '../styles/table.css';

import type { ApiResponse } from '../types';

function ConsumerDetailPage() {
  const { t } = useTranslation('consumer');
  const { consumerId } = useParams();
  const navigate = useNavigate();
  const [subscriptionsLoading, setSubscriptionsLoading] = useState(false);
  const [error, setError] = useState('');
  const [consumer, setConsumer] = useState<IConsumer>();
  const [subscriptions, setSubscriptions] = useState<ISubscription[]>([]);
  const [activeTab, setActiveTab] = useState('basic');
  const [refreshIndex, setRefreshIndex] = useState(0);

  const fetchSubscriptions = async (consumerId: string) => {
    setSubscriptionsLoading(true);
    try {
      const response = await APIs.getConsumerSubscriptions(consumerId);
      if (response?.code === 'SUCCESS' && response?.data) {
        const subscriptionsData = response.data.content || [];
        setSubscriptions(subscriptionsData);
      }
    } catch (error) {
      console.error('Failed to fetch subscriptions:', error);
    } finally {
      setSubscriptionsLoading(false);
    }
  };

  useEffect(() => {
    if (!consumerId) return;

    const fetchConsumerDetail = async () => {
      try {
        const response = await APIs.getConsumer({ id: consumerId });
        if (response?.code === 'SUCCESS' && response?.data) {
          setConsumer(response.data);
        }
      } catch (error) {
        console.error('Failed to fetch consumer detail:', error);
        setError(t('detail.loadFailedDescription'));
      }
    };

    fetchConsumerDetail();
  }, [consumerId, t]);

  useEffect(() => {
    if (consumerId) {
      fetchSubscriptions(consumerId);
    }
  }, [consumerId, refreshIndex]);

  if (error) {
    return (
      <Layout>
        <Alert
          className="my-8"
          description={error}
          message={t('detail.loadFailed')}
          showIcon
          type="error"
        />
      </Layout>
    );
  }

  return (
    <Layout>
      {consumer ? (
        <div className="w-full h-full ">
          <div className="min-h-[calc(100vh-96px)] pb-8 bg-white backdrop-blur-xl rounded-[10px] shadow-sm border border-white/40 overflow-hidden">
            <div className="p-6">
              <div className="flex items-center gap-3">
                <ArrowLeftOutlined className="text-sm" onClick={() => navigate('/consumers')} />
                <h1 className="text-2xl font-semibold text-gray-900">{consumer.name}</h1>
              </div>
            </div>
            <Tabs activeKey={activeTab} className="px-6" onChange={setActiveTab} size="large">
              <Tabs.TabPane key="basic" tab={t('detail.basicInfo')}>
                <div className="border border-[#e5e5e5] rounded-lg mt-2">
                  <AuthConfig consumerId={consumerId ?? ''} />
                </div>
              </Tabs.TabPane>

              <Tabs.TabPane key="authorization" tab={t('detail.subscriptions')}>
                <SubscriptionManager
                  consumerId={consumerId ?? ''}
                  loading={subscriptionsLoading}
                  onRefresh={() => setRefreshIndex((v) => v + 1)}
                  onSubscriptionsChange={async (searchParams) => {
                    if (consumerId) {
                      setSubscriptionsLoading(true);
                      try {
                        const params = new URLSearchParams();
                        if (searchParams?.productName) {
                          params.append('productName', searchParams.productName);
                        }
                        if (searchParams?.status) {
                          params.append('status', searchParams.status);
                        }

                        const queryString = params.toString();
                        const url = `/consumers/${consumerId}/subscriptions${queryString ? `?${queryString}` : ''}`;

                        const response: ApiResponse<{
                          content: ISubscription[];
                          totalElements: number;
                        }> = await request.get(url);
                        if (response?.code === 'SUCCESS' && response?.data) {
                          const subscriptionsData = response.data.content || [];
                          setSubscriptions(subscriptionsData);
                        }
                      } catch (error) {
                        console.error('Failed to fetch subscriptions:', error);
                      } finally {
                        setSubscriptionsLoading(false);
                      }
                    }
                  }}
                  subscriptions={subscriptions}
                />
              </Tabs.TabPane>
            </Tabs>
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">{t('detail.loading')}</div>
        </div>
      )}
    </Layout>
  );
}

export default ConsumerDetailPage;
