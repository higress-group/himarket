import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import { message } from "antd";
import { CloseOutlined, PlusOutlined } from "@ant-design/icons";
import { ModelSelector } from "./ModelSelector";
import { Messages } from "./Messages";
import { InputBox } from "./InputBox";
import { SuggestedQuestions } from "./SuggestedQuestions";
import { MultiModelSelector } from "./MultiModelSelector";
import McpModal from "./McpModal";
import useProducts from "../../hooks/useProducts";
import useCategories from "../../hooks/useCategories";
import APIs from "../../lib/apis";

import type { IGetPrimaryConsumerResp, IProductDetail, ISubscription, IAttachment } from "../../lib/apis";
import type { IModelConversation } from "../../types";
import { safeJSONParse } from "../../lib/utils";
import { getProductMcpMetaBatch } from "../../lib/apis/product";
import { hasAvailableEndpoint } from "../../lib/utils/mcpUtils";
import TextType from "../TextType";


interface ChatAreaProps {
  modelConversations: IModelConversation[];
  currentSessionId?: string;
  selectedModel?: IProductDetail;
  generating: boolean,
  isMcpExecuting: boolean;
  onChangeActiveAnswer: (modelId: string, conversationId: string, questionId: string, direction: 'prev' | 'next') => void
  onSendMessage: (message: string, mcps: IProductDetail[], enableWebSearch: boolean, modelMap: Map<string, IProductDetail>, attachments: IAttachment[]) => void;
  onSelectProduct: (product: IProductDetail) => void;
  handleGenerateMessage: (ids: {
    modelId: string;
    conversationId: string;
    questionId: string;
    content: string;
    mcps: IProductDetail[],
    enableWebSearch: boolean;
    modelMap: Map<string, IProductDetail>,
    attachments?: IAttachment[]
  }) => void;

  addModels: (ids: string[]) => void;
  closeModel: (modelId: string) => void;
  chatType?: "TEXT" | "Image";
  onStop?: () => void;
}

export function ChatArea(props: ChatAreaProps) {
  const {
    modelConversations, onChangeActiveAnswer, onSendMessage,
    onSelectProduct, selectedModel, handleGenerateMessage, addModels, closeModel,
    generating, isMcpExecuting, chatType = "TEXT", onStop
  } = props;

  const isCompareMode = modelConversations.length > 1;

  const {
    data: mcpList, get: getMcpList, loading: mcpListLoading,
    set: setMcpList
  } = useProducts({ type: "MCP_SERVER" });
  const { data: modelList } = useProducts({ type: "MODEL_API", ["modelFilter.category"]: chatType });
  const { data: categories } = useCategories({ type: "MODEL_API", addAll: true });
  const { data: mcpCategories } = useCategories({ type: "MCP_SERVER", addAll: true });

  const primaryConsumer = useRef<IGetPrimaryConsumerResp>();

  const [addedMcps, setAddedMcps] = useState<IProductDetail[]>([]);
  const addedMcpsRef = useRef<IProductDetail[]>([]);
  const [mcpSubscripts, setMcpSubscripts] = useState<ISubscription[]>([]);
  const [mcpEndpointProductIds, setMcpEndpointProductIds] = useState<Set<string>>(new Set());
  // 从 consumer subscriptions 派生已订阅的 MCP productId 集合
  useEffect(() => {
    const ids = new Set(mcpSubscripts.filter(s => s.status === 'APPROVED').map(s => s.productId));
    setMcpEndpointProductIds(ids);
  }, [mcpSubscripts]);

  // productId → 是否有可用 endpoint 的映射
  const [mcpEndpointMap, setMcpEndpointMap] = useState<Map<string, boolean>>(new Map());
  useEffect(() => {
    if (!mcpList || mcpList.length === 0) return;
    const productIds = mcpList.map(p => p.productId);
    getProductMcpMetaBatch(productIds).then(res => {
      if (res.code === 'SUCCESS' && res.data) {
        const map = new Map<string, boolean>();
        for (const meta of res.data) {
          map.set(meta.productId, hasAvailableEndpoint(meta));
        }
        setMcpEndpointMap(map);
      }
    }).catch(() => { /* 静默失败，默认允许订阅 */ });
  }, [mcpList]);
  const [modelSubscriptions, setModelSubscriptions] = useState<ISubscription[]>([]);
  const [mcpEnabled, setMcpEnabled] = useState(() => {
    return safeJSONParse(window.localStorage.getItem("mcpEnabled") || "false", false)
  });

  const [enableWebSearch, setEnableWebSearch] = useState(false);

  const [showModelSelector, setShowModelSelector] = useState(false);
  const [autoScrollEnabled, setAutoScrollEnabled] = useState(true);
  const [showMcpModal, setShowMcpModal] = useState(false);
  const scrollContainerRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // 处理滚动事件，检测用户是否手动向上滚动
  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const { scrollTop, scrollHeight, clientHeight } = target;
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


  const handleMcpFilter = useCallback((id: string) => {
    if (id === "added") {
      setMcpList(addedMcps);
    } else {
      getMcpList({
        type: "MCP_SERVER",
        categoryIds: ['all', 'added'].includes(id) ? [] : [id],
      });
    }
  }, [addedMcps, setMcpList, getMcpList]);

  const handleMcpSearch = useCallback((id: string, name: string) => {
    if (id === "added") {
      setAddedMcps(() => addedMcpsRef.current.filter(mcp => mcp.name.includes(name)))
    } else {
      getMcpList({
        type: "MCP_SERVER",
        categoryIds: ['all', 'added'].includes(id) ? [] : [id],
        name
      });
    }
  }, [addedMcps, getMcpList]);

  const toggleMcpModal = useCallback(() => {
    setShowMcpModal(v => !v);
  }, []);

  const handleToggleCompare = () => {
    setShowModelSelector(true);
  }

  const handleSelectModels = (modelIds: string[]) => {
    addModels(modelIds);
    setShowModelSelector(false);
  }

  const handleAddModel = () => {
    // 添加新的对比模型
    setShowModelSelector(true);
  };

  const selectedModelIds = useMemo(() => {
    return modelConversations.map(model => model.id);
  }, [modelConversations]);

  const handleAddMcp = useCallback((product: IProductDetail) => {
    setAddedMcps(v => {
      if (v.length === 10) {
        message.error("最多添加 10 个 MCP 服务")
        return v;
      }
      const res = [product, ...v];
      addedMcpsRef.current = res;
      return res;
    });
  }, []);

  const handleRemoveMcp = useCallback((product: IProductDetail) => {
    setAddedMcps(v => {
      const res = v.filter(i => i.productId !== product.productId);
      addedMcpsRef.current = res;
      return res;
    })
  }, []);

  const handleRemoveAll = useCallback(() => {
    setAddedMcps([]);
    addedMcpsRef.current = []
  }, [])

  const handleQuickSubscribe = useCallback((_product: IProductDetail) => {
    if (!primaryConsumer.current) return;
    // 弹窗内已完成产品订阅，这里只刷新订阅列表
    APIs.getConsumerSubscriptions(primaryConsumer.current.consumerId, { size: 1000 })
      .then(({ data }) => {
        setMcpSubscripts(data.content);
      }).catch(() => {});
  }, []);

  const handleMcpEnable = (enable: boolean) => {
    localStorage.setItem("mcpEnabled", JSON.stringify(enable));
    setMcpEnabled(enable);
  }

  const subscribedModelList = useMemo(() => {
    if (modelSubscriptions.length === 0) return [];
    const approvedProductIds = new Set(modelSubscriptions.map(s => s.productId));
    return modelList.filter(m => approvedProductIds.has(m.productId));
  }, [modelList, modelSubscriptions]);

  const modelMap = useMemo(() => {
    const m = new Map<string, IProductDetail>;
    subscribedModelList.forEach(model => {
      m.set(model.productId, model);
    });
    return m
  }, [subscribedModelList])

  const showWebSearch = useMemo(() => {
    if (modelConversations.length === 0) {
      return selectedModel?.feature?.modelFeature?.webSearch || false;
    }
    return modelConversations.some(v => {
      return modelMap.get(v.id)?.feature?.modelFeature?.webSearch || false;
    });
  }, [modelConversations, modelMap, selectedModel]);

  const enableMultiModal = useMemo(() => {
    if (modelConversations.length === 0) {
      return selectedModel?.feature?.modelFeature?.enableMultiModal || false;
    }
    return modelConversations.some(v => {
      return modelMap.get(v.id)?.feature?.modelFeature?.enableMultiModal || false;
    });
  }, [modelConversations, modelMap, selectedModel]);

  useEffect(() => {
    APIs.getPrimaryConsumer()
      .then(({ data }) => {
        primaryConsumer.current = data;
        APIs.getConsumerSubscriptions(data.consumerId, { size: 1000 })
          .then(({ data }) => {
            setMcpSubscripts(data.content);
            setModelSubscriptions(data.content.filter(
              (s: ISubscription) => s.status === 'APPROVED'
            ));
          })
      })
  }, []);

  return (
    <div className="h-full flex flex-col flex-1">

      <div className={`overflow-hidden ${modelConversations.length === 0 ? "" : "h-full"} grid grid-rows-[auto] ${modelConversations.length === 0 ? "" : modelConversations.length === 1 ? "grid-cols-1 " : modelConversations.length === 2 ? "grid-cols-2" : "grid-cols-3"}`}>
        {/* 主要内容区域 */}
        {
          modelConversations.map((model, index) => {
            const currentModel = subscribedModelList.find(m => m.productId === model.id);
            return (
              <div
                key={model.id}
                className={`h-full overflow-auto flex-1 flex flex-col ${index < modelConversations.length - 1 ? 'border-r border-gray-200' : ''}`}
              >
                {
                  !isCompareMode && (
                    <div className="">
                      <div className="h-20 flex items-center gap-4 px-4 py-4">
                        <ModelSelector
                          selectedModelId={model.id}
                          onSelectModel={onSelectProduct}
                          modelList={subscribedModelList}
                          // loading={modelsLoading}
                          categories={categories}
                        // categoriesLoading={categoriesLoading}
                        />

                        {/* 分割线 */}
                        <div className="h-6 w-px bg-gray-300"></div>

                        <button
                          onClick={handleToggleCompare}
                          className=" flex items-center gap-2 px-4 py-2 rounded-full border border-gray-300 text-gray-600 bg-transparent hover:border-colorPrimary hover:text-colorPrimary transition-all duration-300 "
                        >
                          <PlusOutlined />
                          <span className="text-sm font-medium">多模型对比</span>
                        </button>
                      </div>


                    </div>
                  )
                }
                {
                  showModelSelector && (
                    <MultiModelSelector
                      currentModelId={model.id}
                      excludeModels={selectedModelIds}
                      onConfirm={handleSelectModels}
                      onCancel={() => setShowModelSelector(false)}
                      modelList={subscribedModelList}
                    // loading={modelsLoading}
                    />
                  )
                }

                {/* 模型名称标题（可切换） + 关闭按钮 + 添加按钮 */}
                {
                  isCompareMode && (
                    <div className="px-4 py-3 flex items-center justify-between">
                      <button className="flex items-center gap-1 text-sm font-semibold text-gray-900 hover:text-colorPrimary transition-colors">
                        {currentModel?.name || "-"}
                      </button>
                      <div className="flex items-center gap-2">
                        {index === 1 && modelConversations.length < 3 && (
                          <button
                            onClick={handleAddModel}
                            className="text-gray-400 hover:text-colorPrimary transition-colors duration-200"
                            title="添加对比模型"
                          >
                            <PlusOutlined className="text-xs" />
                          </button>
                        )}
                        <button
                          onClick={() => closeModel(model.id)}
                          className="text-gray-400 hover:text-gray-600 transition-colors duration-200"
                        >
                          <CloseOutlined className="text-xs" />
                        </button>
                      </div>
                    </div>
                  )
                }

                {/* 消息列表 */}
                <div
                  className="h-full overflow-auto"
                  onScroll={handleScroll}
                  ref={(el) => {
                    if (el) scrollContainerRefs.current.set(model.id, el);
                  }}
                >
                  <Messages
                    conversations={model.conversations}
                    onChangeVersion={(...args) => onChangeActiveAnswer(model.id, ...args)}
                    autoScrollEnabled={autoScrollEnabled}
                    modelName={currentModel?.name}
                    onRefresh={(con, quest, isLast) => {
                      setAutoScrollEnabled(isLast);
                      handleGenerateMessage({
                        modelId: model.id,
                        conversationId: con.id,
                        questionId: quest.id,
                        content: quest.content,
                        mcps: mcpEnabled ? addedMcps : [],
                        enableWebSearch,
                        modelMap,
                        attachments: quest.attachments as IAttachment[],
                      })
                    }}
                  />
                </div>
              </div>
            )
          })
        }
        {
          modelConversations.length === 0 && (
            <div className="">
              <div className="h-20 flex items-center gap-4 px-4 py-4">
                <ModelSelector
                  selectedModelId={selectedModel?.productId || ""}
                  onSelectModel={onSelectProduct}
                  modelList={subscribedModelList}
                  // loading={modelsLoading}
                  categories={categories}
                // categoriesLoading={categoriesLoading}
                />

                {/* 分割线 */}
                <div className="h-6 w-px bg-gray-300"></div>

                <button
                  onClick={handleToggleCompare}
                  className=" flex items-center gap-2 px-4 py-2 rounded-full border border-gray-300 text-gray-600 bg-transparent hover:border-colorPrimary hover:text-colorPrimary transition-all duration-300 "
                >
                  <PlusOutlined />
                  <span className="text-sm font-medium">多模型对比</span>
                </button>
              </div>
              {
                showModelSelector && (
                  <MultiModelSelector
                    currentModelId={selectedModel?.productId || ""}
                    excludeModels={[]}
                    onConfirm={handleSelectModels}
                    onCancel={() => setShowModelSelector(false)}
                    modelList={subscribedModelList}
                  // loading={modelsLoading}
                  />
                )
              }

            </div>
          )
        }

      </div>
      {
        modelConversations.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full px-4">
            <div className="max-w-4xl w-full">
              {/* 欢迎标题 */}
              <div className="text-center mb-12">
                <h1 className="text-2xl font-medium text-gray-900 mb-2">
                  您好，欢迎来到 <span className="text-colorPrimary">
                    <TextType
                      text={["HiMarket HiChat"]}
                      typingSpeed={100}
                      showCursor={true}
                      cursorCharacter="_"
                    />
                  </span>
                </h1>
              </div>

              {/* 输入框 */}
              <div className="mb-8">
                <InputBox
                  onSendMessage={(c, a) => {
                    setAutoScrollEnabled(true);
                    onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, a)
                  }}
                  isLoading={generating}
                  onMcpClick={toggleMcpModal}
                  mcpEnabled={mcpEnabled}
                  addedMcps={addedMcps}
                  isMcpExecuting={isMcpExecuting}
                  showWebSearch={showWebSearch}
                  onWebSearchEnable={setEnableWebSearch}
                  webSearchEnabled={enableWebSearch}
                  enableMultiModal={enableMultiModal}
                  onStop={onStop}
                />
              </div>

              {/* 推荐问题 */}
              <SuggestedQuestions
                onSelectQuestion={(c) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, []);
                }} />
            </div>
          </div>
        ) : (
          <div className="p-4 pb-0">
            <div className="max-w-3xl mx-auto">
              <InputBox
                onMcpClick={toggleMcpModal}
                onSendMessage={(c, a) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap, a);
                }}
                isLoading={generating}
                mcpEnabled={mcpEnabled}
                addedMcps={addedMcps}
                isMcpExecuting={isMcpExecuting}
                showWebSearch={showWebSearch}
                onWebSearchEnable={setEnableWebSearch}
                webSearchEnabled={enableWebSearch}
                enableMultiModal={enableMultiModal}
                onStop={onStop}
              />
            </div>
          </div>
        )
      }
      <McpModal
        open={showMcpModal}
        categories={mcpCategories}
        data={mcpList}
        onFilter={handleMcpFilter}
        onSearch={handleMcpSearch}
        mcpLoading={mcpListLoading}
        added={addedMcps}
        subscripts={mcpSubscripts}
        subscribedProductIds={mcpEndpointProductIds}
        endpointMap={mcpEndpointMap}
        onAdd={handleAddMcp}
        onEnabled={handleMcpEnable}
        enabled={mcpEnabled}
        onRemove={handleRemoveMcp}
        onClose={() => setShowMcpModal(false)}
        onQuickSubscribe={handleQuickSubscribe}
        onRemoveAll={handleRemoveAll}
      />
    </div>
  );
}
