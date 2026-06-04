import { CloseOutlined, PlusOutlined } from '@ant-design/icons';
import { message } from 'antd';
import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';

import { InputBox } from './InputBox';
import McpModal from './McpModal';
import { Messages } from './Messages';
import { ModelSelector } from './ModelSelector';
import { MultiModelSelector } from './MultiModelSelector';
import { SuggestedQuestions } from './SuggestedQuestions';
import useCategories from '../../hooks/useCategories';
import useProducts from '../../hooks/useProducts';
import APIs from '../../lib/apis';
import { safeJSONParse } from '../../lib/utils';
import { ProductIconRenderer } from '../icon/ProductIconRenderer';
import TextType from '../TextType';

import type {
  IGetPrimaryConsumerResp,
  IProductDetail,
  ISubscription,
  IAttachment,
} from '../../lib/apis';
import type { IModelConversation } from '../../types';

interface ChatAreaProps {
  modelConversations: IModelConversation[];
  currentSessionId?: string;
  selectedModel?: IProductDetail;
  generating: boolean;
  isMcpExecuting: boolean;
  onChangeActiveAnswer: (
    modelId: string,
    conversationId: string,
    questionId: string,
    direction: 'prev' | 'next',
  ) => void;
  onSendMessage: (
    message: string,
    mcps: IProductDetail[],
    enableWebSearch: boolean,
    modelMap: Map<string, IProductDetail>,
    attachments: IAttachment[],
  ) => void;
  onSelectProduct: (product: IProductDetail) => void;
  handleGenerateMessage: (ids: {
    modelId: string;
    conversationId: string;
    questionId: string;
    content: string;
    mcps: IProductDetail[];
    enableWebSearch: boolean;
    modelMap: Map<string, IProductDetail>;
    attachments?: IAttachment[];
  }) => void;

  addModels: (ids: string[]) => void;
  closeModel: (modelId: string) => void;
  chatType?: 'TEXT' | 'Image';
  onStop?: () => void;
}

export function ChatArea(props: ChatAreaProps) {
  const {
    addModels,
    chatType = 'TEXT',
    closeModel,
    generating,
    handleGenerateMessage,
    isMcpExecuting,
    modelConversations,
    onChangeActiveAnswer,
    onSelectProduct,
    onSendMessage,
    onStop,
    selectedModel,
  } = props;
  const { t } = useTranslation('chat');

  const isCompareMode = modelConversations.length > 1;

  const {
    data: mcpList,
    get: getMcpList,
    loading: mcpListLoading,
    set: setMcpList,
  } = useProducts({ type: 'MCP_SERVER' });
  const { data: modelList } = useProducts({
    ['modelFilter.category']: chatType,
    type: 'MODEL_API',
  });
  const { data: categories } = useCategories({ addAll: true, type: 'MODEL_API' });
  const { data: mcpCategories } = useCategories({ addAll: true, type: 'MCP_SERVER' });

  const primaryConsumer = useRef<IGetPrimaryConsumerResp>();

  const [addedMcps, setAddedMcps] = useState<IProductDetail[]>([]);
  const addedMcpsRef = useRef<IProductDetail[]>([]);
  const [mcpSubscripts, setMcpSubscripts] = useState<ISubscription[]>([]);
  const [modelSubscriptions, setModelSubscriptions] = useState<ISubscription[]>([]);
  const [mcpEnabled, setMcpEnabled] = useState(() => {
    return safeJSONParse(window.localStorage.getItem('mcpEnabled') || 'false', false);
  });

  const [enableWebSearch, setEnableWebSearch] = useState(false);

  const [showModelSelector, setShowModelSelector] = useState(false);
  const [autoScrollEnabled, setAutoScrollEnabled] = useState(true);
  const [showMcpModal, setShowMcpModal] = useState(false);
  const scrollContainerRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // 处理滚动事件，检测用户是否手动向上滚动
  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const { clientHeight, scrollHeight, scrollTop } = target;
    // 距离底部的阈值（像素）
    const threshold = 100;
    const isAtBottom = scrollHeight - scrollTop - clientHeight < threshold;

    if (isAtBottom) {
      // 用户滚动到底部，恢复自动滚动
      setAutoScrollEnabled(true);
    } else {
      // 用户向上滚动，禁用自动滚动
      setAutoScrollEnabled(false);
    }
  }, []);

  const handleMcpFilter = useCallback(
    (id: string) => {
      if (id === 'added') {
        setMcpList(addedMcps);
      } else {
        getMcpList({
          categoryIds: ['all', 'added'].includes(id) ? [] : [id],
          type: 'MCP_SERVER',
        });
      }
    },
    [addedMcps, setMcpList, getMcpList],
  );

  const handleMcpSearch = useCallback(
    (id: string, name: string) => {
      if (id === 'added') {
        setAddedMcps(() => addedMcpsRef.current.filter((mcp) => mcp.name.includes(name)));
      } else {
        getMcpList({
          categoryIds: ['all', 'added'].includes(id) ? [] : [id],
          name,
          type: 'MCP_SERVER',
        });
      }
    },
    [getMcpList],
  );

  const toggleMcpModal = useCallback(() => {
    setShowMcpModal((v) => !v);
  }, []);

  const handleToggleCompare = () => {
    setShowModelSelector(true);
  };

  const handleSelectModels = (modelIds: string[]) => {
    addModels(modelIds);
    setShowModelSelector(false);
  };

  const handleAddModel = () => {
    // 添加新的对比模型
    setShowModelSelector(true);
  };

  const selectedModelIds = useMemo(() => {
    return modelConversations.map((model) => model.id);
  }, [modelConversations]);

  const handleAddMcp = useCallback(
    (product: IProductDetail) => {
      setAddedMcps((v) => {
        if (v.length === 10) {
          message.error(t('mcp.maxAdded'));
          return v;
        }
        const res = [product, ...v];
        addedMcpsRef.current = res;
        return res;
      });
    },
    [t],
  );

  const handleRemoveMcp = useCallback((product: IProductDetail) => {
    setAddedMcps((v) => {
      const res = v.filter((i) => i.productId !== product.productId);
      addedMcpsRef.current = res;
      return res;
    });
  }, []);

  const handleRemoveAll = useCallback(() => {
    setAddedMcps([]);
    addedMcpsRef.current = [];
  }, []);

  const handleQuickSubscribe = useCallback(
    (product: IProductDetail) => {
      if (!primaryConsumer.current) return;
      APIs.subscribeProduct(primaryConsumer.current.consumerId, product.productId)
        .then(({ data }) => {
          if (data) {
            message.success(t('mcp.subscribeSuccess'));
            APIs.getConsumerSubscriptions(data.consumerId, { size: 1000 }).then(({ data }) => {
              setMcpSubscripts(data.content);
            });
          } else {
            message.error(t('mcp.subscribeFailed'));
          }
        })
        .catch(() => {
          message.error(t('mcp.subscribeFailed'));
        });
    },
    [t],
  );

  const handleMcpEnable = (enable: boolean) => {
    localStorage.setItem('mcpEnabled', JSON.stringify(enable));
    setMcpEnabled(enable);
  };

  const subscribedModelList = useMemo(() => {
    const modelApiSubs = modelSubscriptions.filter(
      (s) => s.status === 'APPROVED' && s.productType === 'MODEL_API',
    );
    if (modelApiSubs.length === 0) {
      return modelList;
    }
    const approvedProductIds = new Set(modelApiSubs.map((s) => s.productId));
    return modelList.filter((m) => approvedProductIds.has(m.productId));
  }, [modelList, modelSubscriptions]);

  const modelMap = useMemo(() => {
    const m = new Map<string, IProductDetail>();
    subscribedModelList.forEach((model) => {
      m.set(model.productId, model);
    });
    return m;
  }, [subscribedModelList]);

  const showWebSearch = useMemo(() => {
    if (modelConversations.length === 0) {
      return selectedModel?.feature?.modelFeature?.webSearch || false;
    }
    return modelConversations.some((v) => {
      return modelMap.get(v.id)?.feature?.modelFeature?.webSearch || false;
    });
  }, [modelConversations, modelMap, selectedModel]);

  const enableMultiModal = useMemo(() => {
    if (modelConversations.length === 0) {
      return selectedModel?.feature?.modelFeature?.enableMultiModal || false;
    }
    return modelConversations.some((v) => {
      return modelMap.get(v.id)?.feature?.modelFeature?.enableMultiModal || false;
    });
  }, [modelConversations, modelMap, selectedModel]);

  useEffect(() => {
    APIs.getPrimaryConsumer().then(({ data }) => {
      primaryConsumer.current = data;
      APIs.getConsumerSubscriptions(data.consumerId, { size: 1000 }).then(({ data }) => {
        setMcpSubscripts(data.content);
        setModelSubscriptions(data.content.filter((s: ISubscription) => s.status === 'APPROVED'));
      });
    });
  }, []);

  return (
    <div className="flex h-full min-w-0 flex-1 flex-col overflow-hidden rounded-[22px] bg-white/[0.34] shadow-[0_18px_50px_rgba(66,76,112,0.045)] backdrop-blur-md">
      <div
        className={`${modelConversations.length === 0 ? 'overflow-visible' : 'grid min-h-0 flex-1 overflow-hidden'} ${modelConversations.length === 0 ? '' : modelConversations.length === 1 ? 'grid-cols-1' : modelConversations.length === 2 ? 'grid-cols-2' : 'grid-cols-3'} ${isCompareMode ? 'gap-4 p-3' : ''}`}
      >
        {/* 主要内容区域 */}
        {modelConversations.map((model, index) => {
          const currentModel = subscribedModelList.find((m) => m.productId === model.id);
          return (
            <div
              className={
                isCompareMode
                  ? 'flex min-h-0 flex-1 flex-col overflow-hidden rounded-[18px] border border-[#DDE5EF] bg-[#FBFCFF] shadow-[0_8px_22px_rgba(66,76,112,0.055)]'
                  : 'flex min-h-0 flex-1 flex-col overflow-hidden'
              }
              key={model.id}
            >
              {!isCompareMode && (
                <div>
                  <div className="flex min-h-16 flex-wrap items-center gap-3 px-4 py-3 sm:gap-4 sm:px-5">
                    <ModelSelector
                      // loading={modelsLoading}
                      categories={categories}
                      modelList={subscribedModelList}
                      onSelectModel={onSelectProduct}
                      selectedModelId={model.id}
                      // categoriesLoading={categoriesLoading}
                    />

                    <button
                      className="flex h-11 flex-shrink-0 items-center gap-2.5 rounded-[12px] border border-white/60 bg-white/50 px-3.5 pr-4 text-gray-700 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)] backdrop-blur-md transition-all duration-200 hover:border-white/80 hover:bg-white/70 hover:text-colorPrimary hover:shadow-[0_8px_22px_rgba(37,56,88,0.06)] active:scale-[0.98]"
                      onClick={handleToggleCompare}
                    >
                      <span className="flex h-7 w-7 items-center justify-center rounded-[8px] border border-white/60 bg-white/60 text-colorPrimary shadow-[inset_0_1px_0_rgba(255,255,255,0.65)]">
                        <PlusOutlined className="text-sm" />
                      </span>
                      <span className="text-sm font-medium">{t('area.multiModelCompare')}</span>
                    </button>
                  </div>
                </div>
              )}
              {showModelSelector && (
                <MultiModelSelector
                  currentModelId={model.id}
                  excludeModels={selectedModelIds}
                  modelList={subscribedModelList}
                  onCancel={() => setShowModelSelector(false)}
                  onConfirm={handleSelectModels}
                  // loading={modelsLoading}
                />
              )}

              {/* 模型名称标题（可切换） + 关闭按钮 + 添加按钮 */}
              {isCompareMode && (
                <div className="flex min-h-[58px] items-center justify-between gap-3 px-4 py-2.5">
                  <button className="flex min-w-0 items-center gap-2.5 text-left transition-colors hover:text-colorPrimary">
                    <span className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-[10px] border border-[#E5EBF3] bg-[#F8FAFC] text-colorPrimary">
                      <ProductIconRenderer
                        className="h-5 w-5"
                        iconType={currentModel?.icon?.value}
                      />
                    </span>
                    <span className="truncate text-sm font-semibold text-gray-900">
                      {currentModel?.name || '-'}
                    </span>
                  </button>
                  <div className="flex flex-shrink-0 items-center gap-1">
                    {index === 1 && modelConversations.length < 3 && (
                      <button
                        className="flex h-8 w-8 items-center justify-center rounded-[10px] text-gray-400 transition-colors duration-200 hover:bg-white/75 hover:text-colorPrimary"
                        onClick={handleAddModel}
                        title={t('area.addCompareModel')}
                        type="button"
                      >
                        <PlusOutlined className="text-xs" />
                      </button>
                    )}
                    <button
                      className="flex h-8 w-8 items-center justify-center rounded-[10px] text-gray-400 transition-colors duration-200 hover:bg-white/75 hover:text-gray-700"
                      onClick={() => closeModel(model.id)}
                      type="button"
                    >
                      <CloseOutlined className="text-xs" />
                    </button>
                  </div>
                </div>
              )}

              {/* 消息列表 */}
              <div
                className="min-h-0 flex-1 overflow-auto"
                onScroll={handleScroll}
                ref={(el) => {
                  if (el) scrollContainerRefs.current.set(model.id, el);
                }}
              >
                <Messages
                  autoScrollEnabled={autoScrollEnabled}
                  conversations={model.conversations}
                  modelIcon={currentModel?.icon?.value}
                  modelName={currentModel?.name}
                  onChangeVersion={(...args) => onChangeActiveAnswer(model.id, ...args)}
                  onRefresh={(con, quest, isLast) => {
                    setAutoScrollEnabled(isLast);
                    handleGenerateMessage({
                      attachments: quest.attachments as IAttachment[],
                      content: quest.content,
                      conversationId: con.id,
                      enableWebSearch,
                      mcps: mcpEnabled ? addedMcps : [],
                      modelId: model.id,
                      modelMap,
                      questionId: quest.id,
                    });
                  }}
                  variant={isCompareMode ? 'compare' : 'default'}
                />
              </div>
            </div>
          );
        })}
        {modelConversations.length === 0 && (
          <div>
            <div className="flex min-h-16 flex-wrap items-center gap-3 px-4 py-3 sm:gap-4 sm:px-5">
              <ModelSelector
                // loading={modelsLoading}
                categories={categories}
                modelList={subscribedModelList}
                onSelectModel={onSelectProduct}
                selectedModelId={selectedModel?.productId || ''}
                // categoriesLoading={categoriesLoading}
              />

              <button
                className="flex h-11 flex-shrink-0 items-center gap-2.5 rounded-[12px] border border-white/60 bg-white/50 px-3.5 pr-4 text-gray-700 shadow-[inset_0_1px_0_rgba(255,255,255,0.6)] backdrop-blur-md transition-all duration-200 hover:border-white/80 hover:bg-white/70 hover:text-colorPrimary hover:shadow-[0_8px_22px_rgba(37,56,88,0.06)] active:scale-[0.98]"
                onClick={handleToggleCompare}
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-[8px] border border-white/60 bg-white/60 text-colorPrimary shadow-[inset_0_1px_0_rgba(255,255,255,0.65)]">
                  <PlusOutlined className="text-sm" />
                </span>
                <span className="text-sm font-medium">{t('area.multiModelCompare')}</span>
              </button>
            </div>
            {showModelSelector && (
              <MultiModelSelector
                currentModelId={selectedModel?.productId || ''}
                excludeModels={[]}
                modelList={subscribedModelList}
                onCancel={() => setShowModelSelector(false)}
                onConfirm={handleSelectModels}
                // loading={modelsLoading}
              />
            )}
          </div>
        )}
      </div>
      {modelConversations.length === 0 ? (
        <div className="flex min-h-0 flex-1 flex-col items-center justify-center px-5 pb-10">
          <div className="w-full max-w-[920px]">
            {/* 欢迎标题 */}
            <div className="mb-9 text-center">
              <h1 className="mb-2 text-[28px] font-semibold tracking-normal text-gray-950">
                {t('area.emptyTitle')}{' '}
                <span className="text-colorPrimary">
                  <TextType
                    cursorCharacter="_"
                    showCursor={true}
                    text={['HiChat']}
                    typingSpeed={100}
                  />
                </span>
              </h1>
            </div>

            {/* 输入框 */}
            <div className="mb-7">
              <InputBox
                addedMcps={addedMcps}
                enableMultiModal={enableMultiModal}
                isLoading={generating}
                isMcpExecuting={isMcpExecuting}
                mcpEnabled={mcpEnabled}
                onMcpClick={toggleMcpModal}
                onSendMessage={(c, a) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, a);
                }}
                onStop={onStop}
                onWebSearchEnable={setEnableWebSearch}
                showWebSearch={showWebSearch}
                webSearchEnabled={enableWebSearch}
              />
            </div>

            {/* 推荐问题 */}
            <SuggestedQuestions
              onSelectQuestion={(c) => {
                setAutoScrollEnabled(true);
                onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, []);
              }}
            />
          </div>
        </div>
      ) : (
        <div className="p-4 pt-3">
          <div className="mx-auto max-w-[1040px]">
            <InputBox
              addedMcps={addedMcps}
              enableMultiModal={enableMultiModal}
              isLoading={generating}
              isMcpExecuting={isMcpExecuting}
              mcpEnabled={mcpEnabled}
              onMcpClick={toggleMcpModal}
              onSendMessage={(c, a) => {
                setAutoScrollEnabled(true);
                onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, a);
              }}
              onStop={onStop}
              onWebSearchEnable={setEnableWebSearch}
              showWebSearch={showWebSearch}
              webSearchEnabled={enableWebSearch}
            />
          </div>
        </div>
      )}
      <McpModal
        added={addedMcps}
        categories={mcpCategories}
        data={mcpList}
        enabled={mcpEnabled}
        mcpLoading={mcpListLoading}
        onAdd={handleAddMcp}
        onClose={() => setShowMcpModal(false)}
        onEnabled={handleMcpEnable}
        onFilter={handleMcpFilter}
        onQuickSubscribe={handleQuickSubscribe}
        onRemove={handleRemoveMcp}
        onRemoveAll={handleRemoveAll}
        onSearch={handleMcpSearch}
        open={showMcpModal}
        subscripts={mcpSubscripts}
      />
    </div>
  );
}
