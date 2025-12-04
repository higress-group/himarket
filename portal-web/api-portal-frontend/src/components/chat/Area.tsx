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

import type { IGetPrimaryConsumerResp, IProductDetail, ISubscription } from "../../lib/apis";
import type { IModelConversation } from "../../types";
import { safeJSONParse } from "../../lib/utils";
import TextType from "../TextType";


interface ChatAreaProps {
  modelConversations: IModelConversation[];
  currentSessionId?: string;
  selectedModel?: IProductDetail;
  generating: boolean,
  isMcpExecuting: boolean;
  onChangeActiveAnswer: (modelId: string, conversationId: string, questionId: string, direction: 'prev' | 'next') => void
  onSendMessage: (message: string, mcps: IProductDetail[], enableWebSearch: boolean, modelMap: Map<string, IProductDetail>) => void;
  onSelectProduct: (product: IProductDetail) => void;
  handleGenerateMessage: (ids: {
    modelId: string;
    conversationId: string;
    questionId: string;
    content: string;
    mcps: IProductDetail[],
    enableWebSearch: boolean;
    modelMap: Map<string, IProductDetail>
  }) => void;

  addModels: (ids: string[]) => void;
  closeModel: (modelId: string) => void;
}

export function ChatArea(props: ChatAreaProps) {
  const {
    modelConversations, onChangeActiveAnswer, onSendMessage,
    onSelectProduct, selectedModel, handleGenerateMessage, addModels, closeModel,
    generating, isMcpExecuting
  } = props;

  const isCompareMode = modelConversations.length > 1;

  const {
    data: mcpList, get: getMcpList, loading: mcpListLoading,
    set: setMcpList
  } = useProducts({ type: "MCP_SERVER" });
  const { data: modelList } = useProducts({ type: "MODEL_API" });
  const { data: categories } = useCategories({ type: "MODEL_API", addAll: true });
  const { data: mcpCategories } = useCategories({ type: "MCP_SERVER", addAll: true });

  const primaryConsumer = useRef<IGetPrimaryConsumerResp>();

  const [addedMcps, setAddedMcps] = useState<IProductDetail[]>([]);
  const addedMcpsRef = useRef<IProductDetail[]>([]);
  const [mcpSubscripts, setMcpSubscripts] = useState<ISubscription[]>([]);
  const [mcpEnabled, setMcpEnabled] = useState(() => {
    return safeJSONParse(window.localStorage.getItem("mcpEnabled") || "false", false)
  });

  const [enableWebSearch, setEnableWebSearch] = useState(false);

  const [showModelSelector, setShowModelSelector] = useState(false);
  const [autoScrollEnabled, setAutoScrollEnabled] = useState(true);
  const [showMcpModal, setShowMcpModal] = useState(false);


  const handleMcpFilter = useCallback((id: string) => {
    if (id === "added") {
      setMcpList(addedMcps);
    } else {
      getMcpList({
        type: "MCP_SERVER",
        categoryIds: ['all', 'added'].includes(id) ? [] : [id],
      });
    }
  }, [addedMcps]);

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
  }, [addedMcps]);

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

  const handleQuickSubscribe = useCallback((product: IProductDetail) => {
    if (!primaryConsumer.current) return;
    APIs.subscribeProduct(primaryConsumer.current.consumerId, product.productId)
      .then(({ data }) => {
        if (data) {
          message.success("订阅成功")
          APIs.getConsumerSubscriptions(data.consumerId, { size: 1000 })
            .then(({ data }) => {
              setMcpSubscripts(data.content);
            })
        } else {
          message.error("订阅失败")
        }
      }).catch(() => {
        message.error("订阅失败")
      })
  }, []);

  const handleMcpEnable = (enable: boolean) => {
    localStorage.setItem("mcpEnabled", JSON.stringify(enable));
    setMcpEnabled(enable);
  }

  const modelMap = useMemo(() => {
    const m = new Map<string, IProductDetail>;
    modelList.forEach(model => {
      m.set(model.productId, model);
    });
    return m
  }, [modelList])

  const showWebSearch = useMemo(() => {
    if (modelConversations.length === 0) {
      return selectedModel?.feature?.modelFeature?.webSearch || false;
    }
    return modelConversations.some(v => {
      return modelMap.get(v.id)?.feature?.modelFeature?.webSearch || false;
    });
  }, [modelConversations, modelMap, selectedModel]);

  useEffect(() => {
    APIs.getPrimaryConsumer()
      .then(({ data }) => {
        primaryConsumer.current = data;
        APIs.getConsumerSubscriptions(data.consumerId, { size: 1000 })
          .then(({ data }) => {
            setMcpSubscripts(data.content);
          })
      })
  }, []);

  return (
    <div className="h-full flex flex-col flex-1">

      <div className={`overflow-auto ${modelConversations.length === 0 ? "" : "h-full"} grid grid-rows-[auto] ${modelConversations.length === 0 ? "" : modelConversations.length === 1 ? "grid-cols-1 " : modelConversations.length === 2 ? "grid-cols-2" : "grid-cols-3"}`}>
        {/* 主要内容区域 */}
        {
          modelConversations.map((model, index) => {
            const currentModel = modelList.find(m => m.productId === model.id);
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
                          modelList={modelList}
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
                      modelList={modelList}
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
                <div className="h-full overflow-auto">
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
                  modelList={modelList}
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
                    modelList={modelList}
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
                      text={["Himarket 体验中心_"]}
                      typingSpeed={75}
                      showCursor={true}
                      cursorCharacter="|"
                    />
                  </span>
                </h1>
              </div>

              {/* 输入框 */}
              <div className="mb-8">
                <InputBox
                  onSendMessage={(c) => {
                    setAutoScrollEnabled(true);
                    onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap)
                  }}
                  isLoading={generating}
                  onMcpClick={toggleMcpModal}
                  mcpEnabled={mcpEnabled}
                  addedMcps={addedMcps}
                  isMcpExecuting={isMcpExecuting}
                  showWebSearch={showWebSearch}
                  onWebSearchEnable={setEnableWebSearch}
                  webSearchEnabled={enableWebSearch}
                />
              </div>

              {/* 推荐问题 */}
              <SuggestedQuestions
                onSelectQuestion={(c) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap);
                }} />
            </div>
          </div>
        ) : (
          <div className="p-4 pb-0">
            <div className="max-w-3xl mx-auto">
              <InputBox
                onMcpClick={toggleMcpModal}
                onSendMessage={(c) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c, mcpEnabled ? addedMcps : [], enableWebSearch, modelMap);
                }}
                isLoading={generating}
                mcpEnabled={mcpEnabled}
                addedMcps={addedMcps}
                isMcpExecuting={isMcpExecuting}
                showWebSearch={showWebSearch}
                onWebSearchEnable={setEnableWebSearch}
                webSearchEnabled={enableWebSearch}
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
