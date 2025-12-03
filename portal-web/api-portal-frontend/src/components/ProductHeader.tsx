import React, { useState, useEffect } from "react";
import { Typography, Button, Modal, Select, message, Popconfirm, Input, Pagination, Spin } from "antd";
import { ApiOutlined, CheckCircleFilled, ClockCircleFilled, ExclamationCircleFilled, PlusOutlined, RobotOutlined, BulbOutlined } from "@ant-design/icons";
import { useParams } from "react-router-dom";
import { getConsumers, subscribeProduct, unsubscribeProduct, getProductSubscriptions } from "../lib/api";
import type { Consumer } from "../types/consumer";
import type { IMCPConfig, IProductIcon } from "../lib/apis/typing";
import APIs, { getProductSubscriptionStatus, type ISubscription } from "../lib/apis";

const { Title, Paragraph } = Typography;
const { Search } = Input;

interface ProductHeaderProps {
  name: string;
  description: string;
  icon?: IProductIcon;
  defaultIcon?: string;
  mcpConfig?: IMCPConfig;
  agentConfig?: any;  // 添加 agentConfig 支持，用于判断 Agent 来源
  updatedAt?: string;
  productType?: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
}

type UnwrapPromise<T> = T extends Promise<infer U> ? U : T;

// 处理产品图标的函数
const getIconUrl = (icon?: IProductIcon, defaultIcon?: string): string => {
  const fallback = defaultIcon || "/logo.svg";
  
  if (!icon) {
    return fallback;
  }
  
  switch (icon.type) {
    case "URL":
      return icon.value || fallback;
    case "BASE64":
      // 如果value已经包含data URL前缀，直接使用；否则添加前缀
      return icon.value ? (icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`) : fallback;
    default:
      return fallback;
  }
};

export const ProductHeader: React.FC<ProductHeaderProps> = ({
  name,
  description,
  icon,
  defaultIcon = "/default-icon.png",
  mcpConfig,
  agentConfig,
  updatedAt,
  productType,
}) => {
  const { 
    apiProductId, 
    mcpProductId, 
    agentProductId, 
    modelProductId 
  } = useParams();
  
  const [isManageModalVisible, setIsManageModalVisible] = useState(false);
  const [isApplyingSubscription, setIsApplyingSubscription] = useState(false);
  const [selectedConsumerId, setSelectedConsumerId] = useState<string>('');
  const [consumers, setConsumers] = useState<Consumer[]>([]);
  
  // 分页相关state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(5); // 每页显示5个订阅
  
  // 分开管理不同的loading状态
  const [consumersLoading, setConsumersLoading] = useState(false);
  const [submitLoading, setSubmitLoading] = useState(false);
  const [imageLoadFailed, setImageLoadFailed] = useState(false);

  // 订阅状态相关的state
  const [subscriptionStatus, setSubscriptionStatus] = useState<UnwrapPromise<ReturnType<typeof getProductSubscriptionStatus>>>();
  const [subscriptionLoading, setSubscriptionLoading] = useState(false);
  
  // 订阅详情分页数据（用于管理弹窗）
  const [subscriptionDetails, setSubscriptionDetails] = useState<{
    content: ISubscription[];
    totalElements: number;
    totalPages: number;
  }>({ content: [], totalElements: 0, totalPages: 0 });
  const [detailsLoading, setDetailsLoading] = useState(false);
  
  // 搜索相关state
  const [searchKeyword, setSearchKeyword] = useState("");

  // 判断是否应该显示申请订阅按钮
  // MCP_SERVER: 来自 NACOS 时不显示
  // AGENT_API: 来自 NACOS 时不显示
  // MODEL_API: 始终显示
  // REST_API: 始终显示
  const isNacosAgent = productType === 'AGENT_API' && 
    agentConfig?.meta?.source?.toUpperCase() === 'NACOS';
  const isNacosMcp = productType === 'MCP_SERVER' && 
    mcpConfig?.meta?.source?.toUpperCase() === 'NACOS';
  
  const shouldShowSubscribeButton = 
    productType === 'MODEL_API' ||                      // MODEL_API: 始终显示
    productType === 'REST_API' ||                       // REST_API: 始终显示
    (productType === 'AGENT_API' && !isNacosAgent) ||   // AGENT_API: 非 Nacos 时显示
    (productType === 'MCP_SERVER' && !isNacosMcp);      // MCP_SERVER: 非 Nacos 时显示

  // 获取产品ID - 根据产品类型获取正确的参数
  const productId = apiProductId || mcpProductId || agentProductId || modelProductId || '';

  // 查询订阅状态
  const fetchSubscriptionStatus = async () => {
    if (!productId || !shouldShowSubscribeButton) return;
    
    setSubscriptionLoading(true);
    try {
      const status = await APIs.getProductSubscriptionStatus(productId);
      setSubscriptionStatus(status);
    } catch (error) {
      console.error('获取订阅状态失败:', error);
    } finally {
      setSubscriptionLoading(false);
    }
  };

  // 获取订阅详情（用于管理弹窗）
  const fetchSubscriptionDetails = async (page: number = 1, search: string = ''): Promise<void> => {
    if (!productId) return Promise.resolve();
    
    setDetailsLoading(true);
    try {
      const response = await getProductSubscriptions(productId, {
        consumerName: search.trim() || undefined,
        page: page - 1, // 后端使用0基索引
        size: pageSize
      });
      
      setSubscriptionDetails({
        content: response.data.content || [],
        totalElements: response.data.totalElements || 0,
        totalPages: response.data.totalPages || 0
      });
    } catch (error) {
      console.error('获取订阅详情失败:', error);
      message.error('获取订阅详情失败，请重试');
    } finally {
      setDetailsLoading(false);
    }
  };

  useEffect(() => {
    fetchSubscriptionStatus();
  }, [productId, shouldShowSubscribeButton]);

  // 获取消费者列表
  const fetchConsumers = async () => {
    try {
      setConsumersLoading(true);
      const response = await getConsumers({}, { page: 1, size: 100 });
      if (response.data) {
        setConsumers(response.data.content || response.data);
      }
    } catch (error) {
      console.log(error)
      // message.error('获取消费者列表失败');
    } finally {
      setConsumersLoading(false);
    }
  };

  // 开始申请订阅流程
  const startApplyingSubscription = () => {
    setIsApplyingSubscription(true);
    setSelectedConsumerId('');
    fetchConsumers();
  };

  // 取消申请订阅
  const cancelApplyingSubscription = () => {
    setIsApplyingSubscription(false);
    setSelectedConsumerId('');
  };

  // 提交申请订阅
  const handleApplySubscription = async () => {
    if (!selectedConsumerId) {
      message.warning('请选择消费者');
      return;
    }

    try {
      setSubmitLoading(true);
      await subscribeProduct(selectedConsumerId, productId);
      message.success('申请提交成功');
      
      // 重置状态
      setIsApplyingSubscription(false);
      setSelectedConsumerId('');
      
      // 重新获取订阅状态和详情数据
      await fetchSubscriptionStatus();
      await fetchSubscriptionDetails(currentPage, '');
    } catch (error) {
      console.error('申请订阅失败:', error);
      message.error('申请提交失败，请重试');
    } finally {
      setSubmitLoading(false);
    }
  };

  // 显示管理弹窗
  const showManageModal = () => {
    setIsManageModalVisible(true);
    
    // 优先使用已缓存的数据，避免重复查询
    if (subscriptionStatus?.fullSubscriptionData) {
      setSubscriptionDetails({
        content: subscriptionStatus.fullSubscriptionData.content,
        totalElements: subscriptionStatus.fullSubscriptionData.totalElements,
        totalPages: subscriptionStatus.fullSubscriptionData.totalPages
      });
      // 重置分页到第一页
      setCurrentPage(1);
      setSearchKeyword('');
    } else {
      // 如果没有缓存数据，则重新获取
      fetchSubscriptionDetails(1, '');
    }
  };

  // 处理搜索输入变化
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setSearchKeyword(value);
    // 只更新状态，不触发搜索
  };

  // 执行搜索
  const handleSearch = (value?: string) => {
    // 如果传入了value参数，使用该参数；否则使用当前的searchKeyword
    const keyword = value !== undefined ? value : searchKeyword;
    const trimmedKeyword = keyword.trim();
    setCurrentPage(1);
    
    // 总是调用API进行搜索，不使用缓存
    fetchSubscriptionDetails(1, trimmedKeyword);
  };

  // 处理回车键搜索
  const handleSearchKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };


  // 隐藏管理弹窗
  const handleManageCancel = () => {
    setIsManageModalVisible(false);
    // 重置申请订阅状态
    setIsApplyingSubscription(false);
    setSelectedConsumerId('');
    // 重置分页和搜索
    setCurrentPage(1);
    setSearchKeyword('');
    // 清空订阅详情数据
    setSubscriptionDetails({ content: [], totalElements: 0, totalPages: 0 });
  };

  // 取消订阅
  const handleUnsubscribe = async (consumerId: string) => {
    try {
      await unsubscribeProduct(consumerId, productId);
      message.success('取消订阅成功');
      
      // 重新获取订阅状态和详情数据
      await fetchSubscriptionStatus();
      await fetchSubscriptionDetails(currentPage, '');
    } catch (error) {
      console.error('取消订阅失败:', error);
      message.error('取消订阅失败，请重试');
    }
  };

  return (
    <>
      <div className="mb-2">
        {/* 第一行：图标和标题信息 */}
        <div className="flex items-center gap-4 mb-3">
          {(!icon || imageLoadFailed) && (productType === 'REST_API' || productType === 'AGENT_API' || productType === 'MODEL_API') ? (
            <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gray-50 border border-gray-200">
              {productType === 'REST_API' ? (
                <ApiOutlined className="text-3xl text-black" />
              ) : productType === 'AGENT_API' ? (
                <RobotOutlined className="text-3xl text-black" />
              ) : (
                <BulbOutlined className="text-3xl text-black" />
              )}
            </div>
          ) : (
            <img
              src={getIconUrl(icon, defaultIcon)}
              alt="icon"
              className="w-16 h-16 rounded-xl object-cover border border-gray-200 flex-shrink-0"
              onError={(e) => {
                const target = e.target as HTMLImageElement;
                if (productType === 'REST_API' || productType === 'AGENT_API' || productType === 'MODEL_API') {
                  setImageLoadFailed(true);
                } else {
                  // 确保有一个最终的fallback图片，避免无限循环请求
                  const fallbackIcon = defaultIcon || "/logo.svg";
                  const currentUrl = new URL(target.src, window.location.href).href;
                  const fallbackUrl = new URL(fallbackIcon, window.location.href).href;
                  if (currentUrl !== fallbackUrl) {
                    target.src = fallbackIcon;
                  }
                }
              }}
            />
          )}
          <div className="flex-1 min-w-0 flex flex-col justify-center">
            <Title level={3} className="mb-1 text-xl font-semibold">
              {name}
            </Title>
            {updatedAt && (
              <div className="text-sm text-gray-400">
                {new Date(updatedAt).toLocaleDateString('zh-CN', {
                  year: 'numeric',
                  month: '2-digit',
                  day: '2-digit'
                }).replace(/\//g, '.')} updated
              </div>
            )}
          </div>
        </div>
        
        {/* 第二行：描述信息，与左边框对齐 */}
        <Paragraph className="text-gray-600 mb-3 text-sm leading-relaxed">
          {description}
        </Paragraph>
        
        {/* 第三行：徽章式订阅状态 + 管理按钮，与左边框对齐 */}
        {shouldShowSubscribeButton && (
          <div className="flex items-center gap-4">
            {subscriptionLoading ? (
              <Button loading>加载中...</Button>
            ) : (
              <>
                {/* 订阅状态徽章 */}
                <div className="flex items-center">
                  {subscriptionStatus?.hasSubscription ? (
                    <>
                      <div className="w-2 h-2 bg-green-500 rounded-full mr-2"></div>
                      <span className="text-sm text-gray-600 font-medium">已订阅</span>
                    </>
                  ) : (
                    <>
                      <div className="w-2 h-2 bg-gray-400 rounded-full mr-2"></div>
                      <span className="text-sm text-gray-600">未订阅</span>
                    </>
                  )}
                </div>
                
                {/* 管理按钮 */}
                <Button
                  type="primary"
                  className="rounded-xl"
                  onClick={showManageModal}
                >
                  管理订阅
                </Button>
              </>
            )}
          </div>
        )}
      </div>


      {/* 订阅管理弹窗 */}
      <Modal
        title="订阅管理"
        open={isManageModalVisible}
        onCancel={handleManageCancel}
        footer={null}
        width={600}
        styles={{
          header: {
            borderRadius: '8px 8px 0 0',
            marginBottom: 0,
            paddingBottom: '8px'
          },
          body: {
            padding: '0px'
          }
        }}
      >
        <div className="px-6 py-4">
          {/* 产品名称标识 - 容器框样式 */}
          <div className="mb-4">
            <div className="bg-blue-50 border border-blue-200 rounded px-3 py-2">
              <span className="text-sm text-gray-600 mr-2">产品名称：</span>
              <span className="text-sm text-gray-600">{name}</span>
            </div>
          </div>
          
          {/* 搜索框 */}
          <div className="mb-4">
            <Search
              placeholder="搜索消费者名称"
              value={searchKeyword}
              onChange={handleSearchChange}
              onSearch={handleSearch}
              onPressEnter={handleSearchKeyPress}
              allowClear
              style={{ width: 250 }}
            />
          </div>
          
          {/* 优化的表格式 - 无表头，内嵌分页 */}
          <div className="border border-gray-200 rounded overflow-hidden">
            {detailsLoading ? (
              <div className="p-8 text-center">
                <Spin size="large" />
              </div>
            ) : subscriptionDetails.content && subscriptionDetails.content.length > 0 ? (
              <>
                {/* 表格内容 */}
                <div className="divide-y divide-gray-100">
                  {(searchKeyword.trim() 
                    ? subscriptionDetails.content 
                    : subscriptionDetails.content.slice((currentPage - 1) * pageSize, currentPage * pageSize)
                  ).map((item) => (
                      <div key={item.consumerId} className="flex items-center px-4 py-3 hover:bg-gray-50">
                        {/* 消费者名称 - 40% */}
                        <div className="flex-1 min-w-0 pr-4">
                          <span className="text-sm text-gray-700 truncate block">
                            {item.consumerName}
                          </span>
                        </div>
                        {/* 状态 - 30% */}
                        <div className="w-24 flex items-center pr-4">
                          {item.status === 'APPROVED' ? (
                            <>
                              <CheckCircleFilled className="text-green-500 mr-1" style={{fontSize: '10px'}} />
                              <span className="text-xs text-gray-700">已通过</span>
                            </>
                          ) : item.status === 'PENDING' ? (
                            <>
                              <ClockCircleFilled className="text-blue-500 mr-1" style={{fontSize: '10px'}} />
                              <span className="text-xs text-gray-700">审核中</span>
                            </>
                          ) : (
                            <>
                              <ExclamationCircleFilled className="text-red-500 mr-1" style={{fontSize: '10px'}} />
                              <span className="text-xs text-gray-700">已拒绝</span>
                            </>
                          )}
                        </div>
                        
                        {/* 操作 - 30% */}
                        <div className="w-20">
                          <Popconfirm
                            title="确定要取消订阅吗？"
                            onConfirm={() => handleUnsubscribe(item.consumerId)}
                            okText="确认"
                            cancelText="取消"
                          >
                            <Button type="link" danger size="small" className="p-0">
                              取消订阅
                            </Button>
                          </Popconfirm>
                        </div>
                      </div>
                    ))}
                </div>
              </>
            ) : (
              <div className="p-8 text-center text-gray-500">
                {searchKeyword ? '未找到匹配的订阅记录' : '暂无订阅记录'}
              </div>
            )}
          </div>
          
          {/* 分页 - 使用Ant Design分页组件，右对齐 */}
          {subscriptionDetails.totalElements > 0 && (
            <div className="mt-3 flex justify-end">
              <Pagination
                current={currentPage}
                total={subscriptionDetails.totalElements}
                pageSize={pageSize}
                size="small"
                showSizeChanger={true}
                showQuickJumper={true}
                onChange={(page, size) => {
                  setCurrentPage(page);
                  if (size !== pageSize) {
                    setPageSize(size);
                  }
                  
                  // 如果有搜索关键词，需要重新查询；否则使用缓存数据
                  if (searchKeyword.trim()) {
                    fetchSubscriptionDetails(page, searchKeyword);
                  }
                  // 无搜索时不需要重新查询，Ant Design会自动处理前端分页
                }}
                onShowSizeChange={(_current, size) => {
                  setPageSize(size);
                  setCurrentPage(1);
                  
                  // 如果有搜索关键词，需要重新查询；否则使用缓存数据
                  if (searchKeyword.trim()) {
                    fetchSubscriptionDetails(1, searchKeyword);
                  }
                  // 无搜索时不需要重新查询，页面大小变化会自动重新渲染
                }}
                showTotal={(total) => `共 ${total} 条`}
                pageSizeOptions={['5', '10', '20']}
                hideOnSinglePage={false}
              />
            </div>
          )}
          
          {/* 申请订阅区域 - 移回底部 */}
          <div className={`border-t pt-3 ${subscriptionDetails.totalElements > 0 ? 'mt-4' : 'mt-2'}`}>
            <div className="flex justify-end">
              {!isApplyingSubscription ? (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  className="rounded-xl"
                  onClick={startApplyingSubscription}
                >
                  订阅
                </Button>
              ) : (
                <div className="w-full">
                  <div className="bg-gray-50 p-4 rounded-xl">
                    <div className="mb-4">
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        选择消费者
                      </label>
                      <Select
                        placeholder="搜索或选择消费者"
                        style={{ width: '100%' }}
                        value={selectedConsumerId}
                        onChange={setSelectedConsumerId}
                        showSearch
                        loading={consumersLoading}
                        filterOption={(input, option) =>
                          (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                        }
                        notFoundContent={consumersLoading ? '加载中...' : '暂无消费者数据'}
                      >
                        {consumers
                          .filter(consumer => {
                            // 过滤掉已经订阅的consumer
                            const isAlreadySubscribed = subscriptionStatus?.subscribedConsumers?.some(
                              item => item.consumer.consumerId === consumer.consumerId
                            );
                            return !isAlreadySubscribed;
                          })
                          .map(consumer => (
                            <Select.Option key={consumer.consumerId} value={consumer.consumerId}>
                              {consumer.name}
                            </Select.Option>
                          ))
                        }
                      </Select>
                    </div>
                    <div className="flex justify-end gap-2">
                      <Button className="rounded-xl" onClick={cancelApplyingSubscription}>
                        取消
                      </Button>
                      <Button
                        type="primary"
                        className="rounded-xl"
                        loading={submitLoading}
                        disabled={!selectedConsumerId}
                        onClick={handleApplySubscription}
                      >
                        确认申请
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </Modal>
    </>
  );
}; 