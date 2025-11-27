import { useState, useEffect } from "react";
import { CloseOutlined, PlusOutlined } from "@ant-design/icons";
import { message as antdMessage } from "antd";
import { ModelSelector } from "./ModelSelector";
import { MessageList } from "./MessageList";
import { Messages } from "./Messages";
import { InputBox } from "./InputBox";
import { SuggestedQuestions } from "./SuggestedQuestions";
import { MultiModelSelector } from "./MultiModelSelector";
import { getIconString } from "../../lib/iconUtils";
import { generateConversationId, generateQuestionId } from "../../lib/uuid";
import { handleSSEStream } from "../../lib/sse";
import type { ICategory, IProductDetail } from "../../lib/apis";
import APIs from "../../lib/apis";
import type { IMessageVersion, IModelConversation } from "../../types";


interface ChatAreaProps {
  modelConversations: IModelConversation[];
  currentSessionId?: string;
  selectedModel?: IProductDetail;

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
}

export function ChatArea(props: ChatAreaProps) {
  const {
    modelConversations, currentSessionId, onChangeActiveAnswer, onSendMessage,
    onSelectProduct, selectedModel, handleGenerateMessage, addModels
  } = props;

  const isCompareMode = modelConversations.length > 1;


  const [modelList, setModelList] = useState<IProductDetail[]>([]);

  const [categories, setCategories] = useState<ICategory[]>([]);

  const [showModelSelector, setShowModelSelector] = useState(false);

  useEffect(() => {
    APIs.getProducts({ type: "MODEL_API" }).then(res => {
      if (res.data?.content) {
        setModelList(res.data.content);
      }
    });

    APIs.getCategoriesByProductType({ productType: "MODEL_API" }).then(res => {
      if (res.data?.content) {
        setCategories([
          {
            categoryId: "all",
            name: "全部",
            description: "",
            createAt: "",
            updatedAt: "",
          },
          ...res.data.content
        ]);
      }
    });
  }, [])

  const handleToggleCompare = () => {
    setShowModelSelector(true);
  }

  const handleSelectModels = (modelIds: string[]) => {
    addModels(modelIds);
    setShowModelSelector(false);
  }


  return (
    <div className="h-full flex flex-col flex-1">

      <div className={`overflow-auto h-full grid grid-rows-[auto] ${modelConversations.length === 0 ? "" : modelConversations.length === 1 ? "grid-cols-1 " : modelConversations.length === 2 ? "grid-cols-2" : "grid-cols-3"}`}>
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
                      {
                        showModelSelector && (
                          <MultiModelSelector
                            currentModelId={model.id}
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
                            // onClick={handleAddModel}
                            className="text-gray-400 hover:text-colorPrimary transition-colors duration-200"
                            title="添加对比模型"
                          >
                            <PlusOutlined className="text-xs" />
                          </button>
                        )}
                        <button
                          // onClick={() => handleCloseModel(modelId)}
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
                    onRefresh={(con, quest) => {
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
      <div className="p-4 pb-0">
        <div className="max-w-3xl mx-auto">
          <InputBox
            onSendMessage={(c) => {
              onSendMessage(c);
              // setAutoScrollEnabled(true);
              // if (isCompareMode) {
              //   handleCompareSendMessage(c);
              // } else {
              //   onSendMessage(c);
              // }
            }}
          // isLoading={isCompareMode ? isCompareModeLoading : isLoading}
          />
        </div>
      </div>
    </div>
  );
}
