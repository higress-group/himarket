import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Alert, Tabs } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { AuthConfig, SubscriptionManager } from "../components/consumer";
import type { ApiResponse } from "../types";
import "../styles/table.css";
import APIs, { type IConsumer, type ISubscription } from "../lib/apis";
import api from "../lib/api";


function ConsumerDetailPage() {
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
      if (response?.code === "SUCCESS" && response?.data) {
        // 从分页数据中提取实际的订阅数组
        const subscriptionsData = response.data.content || [];
        setSubscriptions(subscriptionsData);
      }
    } catch (error) {
      console.error('获取订阅列表失败:', error);
    } finally {
      setSubscriptionsLoading(false);
    }
  };

  useEffect(() => {
    if (!consumerId) return;

    const fetchConsumerDetail = async () => {
      try {
        const response = await APIs.getConsumer({ id: consumerId });
        if (response?.code === "SUCCESS" && response?.data) {
          setConsumer(response.data);
        }
      } catch (error) {
        console.error('获取消费者详情失败:', error);
        setError('加载失败，请稍后重试');
      }
    };

    fetchConsumerDetail();
  }, [consumerId]);

  useEffect(() => {
    if (consumerId) {
      fetchSubscriptions(consumerId);
    }
  }, [consumerId, refreshIndex]);



  if (error) {
    return (
      <Layout>
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          className="my-8" />
      </Layout>
    );
  }

  return (
    <Layout>
      {consumer ? (
        <div className="w-full h-full ">
          <div className="min-h-[calc(100vh-96px)] pb-8 bg-white backdrop-blur-xl rounded-2xl shadow-sm border border-white/40 overflow-hidden">
            {/* 消费者头部 - 返回按钮 + 消费者名称 */}
            <div className="p-6">
              <div className="flex items-center gap-3">
                <ArrowLeftOutlined
                  onClick={() => navigate('/consumers')}
                  className="text-sm"
                />
                <h1 className="text-2xl font-semibold text-gray-900">
                  {consumer.name}
                </h1>
              </div>
            </div>
            {/* Tabs 区域 - glass-morphism 风格 */}
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              size="large"
              className="px-6"
            >
              <Tabs.TabPane tab="基本信息" key="basic">
                <div className="border border-[#e5e5e5] rounded-lg mt-2">
                  <AuthConfig consumerId={consumerId!} />
                </div>
              </Tabs.TabPane>

              <Tabs.TabPane tab="订阅列表" key="authorization">
                <SubscriptionManager
                  onRefresh={() => setRefreshIndex(v => v + 1)}
                  consumerId={consumerId!}
                  subscriptions={subscriptions}
                  onSubscriptionsChange={async (searchParams) => {
                    // 重新获取订阅列表
                    if (consumerId) {
                      setSubscriptionsLoading(true);
                      try {
                        // 构建查询参数
                        const params = new URLSearchParams();
                        if (searchParams?.productName) {
                          params.append('productName', searchParams.productName);
                        }
                        if (searchParams?.status) {
                          params.append('status', searchParams.status);
                        }

                        const queryString = params.toString();
                        const url = `/consumers/${consumerId}/subscriptions${queryString ? `?${queryString}` : ''}`;

                        const response: ApiResponse<{ content: ISubscription[], totalElements: number }> = await api.get(url);
                        if (response?.code === "SUCCESS" && response?.data) {
                          // 从分页数据中提取实际的订阅数组
                          const subscriptionsData = response.data.content || [];
                          setSubscriptions(subscriptionsData);
                        }
                      } catch (error) {
                        console.error('获取订阅列表失败:', error);
                      } finally {
                        setSubscriptionsLoading(false);
                      }
                    }
                  }}
                  loading={subscriptionsLoading}
                />
              </Tabs.TabPane>
            </Tabs>
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">加载中...</div>
        </div>
      )}
    </Layout>
  );
}

export default ConsumerDetailPage;
