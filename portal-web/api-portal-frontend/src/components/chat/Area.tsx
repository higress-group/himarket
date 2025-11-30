import { useState, useMemo, useCallback } from "react";
import { CloseOutlined, PlusOutlined } from "@ant-design/icons";
import { ModelSelector } from "./ModelSelector";
import { Messages } from "./Messages";
import { InputBox } from "./InputBox";
import { SuggestedQuestions } from "./SuggestedQuestions";
import { MultiModelSelector } from "./MultiModelSelector";
import type { IProductDetail } from "../../lib/apis";
import type { IModelConversation } from "../../types";
import McpModal from "./McpModal";
import useProducts from "../../hooks/useProducts";
import useCategories from "../../hooks/useCategories";


interface ChatAreaProps {
  modelConversations: IModelConversation[];
  currentSessionId?: string;
  selectedModel?: IProductDetail;
  generating: boolean,
  onChangeActiveAnswer: (modelId: string, conversationId: string, questionId: string, direction: 'prev' | 'next') => void
  onSendMessage: (message: string) => void;
  onSelectProduct: (product: IProductDetail) => void;
  handleGenerateMessage: (ids: {
    modelId: string;
    conversationId: string;
    questionId: string;
    content: string;
  }) => void;

  addModels: (ids: string[]) => void;
  closeModel: (modelId: string) => void;
}

export function ChatArea(props: ChatAreaProps) {
  const {
    modelConversations, onChangeActiveAnswer, onSendMessage,
    onSelectProduct, selectedModel, handleGenerateMessage, addModels, closeModel,
    generating,
  } = props;

  const isCompareMode = modelConversations.length > 1;

  const { data: mcpList, get: getMcpList } = useProducts({ type: "MCP_SERVER" });
  const { data: modelList, get: getModels } = useProducts({ type: "MODEL_API" });
  const { data: categories, get: getCategories } = useCategories({ type: "MODEL_API", addAll: true });
  const { data: mcpCategories, get: getMcpCategories } = useCategories({ type: "MCP_SERVER", addAll: true });


  const [showModelSelector, setShowModelSelector] = useState(false);
  const [autoScrollEnabled, setAutoScrollEnabled] = useState(true);
  const [showMcpModal, setShowMcpModal] = useState(false);


  const handleMcpFilter = useCallback((id: string) => {

  }, [])

  const toggleMcpModal = useCallback(() => {
    console.log('asd...')
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
                    onRefresh={(con, quest) => {
                      setAutoScrollEnabled(false);
                      handleGenerateMessage({
                        modelId: model.id,
                        conversationId: con.id,
                        questionId: quest.id,
                        content: quest.content,
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
                  您好，欢迎来到 <span className="text-colorPrimary">Himarket 体验中心_</span>
                </h1>
              </div>

              {/* 输入框 */}
              <div className="mb-8">
                <InputBox
                  onSendMessage={(c) => {
                    setAutoScrollEnabled(true);
                    onSendMessage(c)
                  }}
                  isLoading={generating}
                  onMcpClick={toggleMcpModal}
                />
              </div>

              {/* 推荐问题 */}
              <SuggestedQuestions
                onSelectQuestion={(c) => {
                  setAutoScrollEnabled(true);
                  onSendMessage(c);
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
                  onSendMessage(c);
                }}
                isLoading={generating}
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
      />
    </div>
  );
}
