import { useState, useEffect } from "react";
import { AppstoreAddOutlined, CloseOutlined, PlusOutlined, DownOutlined, SearchOutlined, CheckOutlined } from "@ant-design/icons";
import { Dropdown, Input, Tabs, message as antdMessage } from "antd";
import { ModelSelector } from "./ModelSelector";
import { MessageList } from "./MessageList";
import { InputBox } from "./InputBox";
import { SuggestedQuestions } from "./SuggestedQuestions";
import { MultiModelSelector } from "./MultiModelSelector";
import { getIconString } from "../../lib/iconUtils";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";
import { generateConversationId, generateQuestionId } from "../../lib/uuid";
import { handleSSEStream } from "../../lib/sse";
import type { IProductDetail } from "../../lib/apis";
import APIs from "../../lib/apis";

// 模型数据接口（用于组件内部）
interface ModelData {
  id: string;
  name: string;
  description: string;
  category: string;
  icon: string;
  productCategories: string[]; // 产品分类 ID 数组
}

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: Date;
  // AI 返回时的统计信息
  firstTokenTime?: number;
  totalTime?: number;
  inputTokens?: number;
  outputTokens?: number;
  // 多版本答案支持
  questionId?: string;
  versions?: Array<{
    content: string;
    firstTokenTime?: number;
    totalTime?: number;
    inputTokens?: number;
    outputTokens?: number;
  }>;
  currentVersionIndex?: number;
  // 错误状态
  error?: boolean;
  errorMessage?: string;
}

interface ChatAreaProps {
  messages: Message[];
  selectedProduct?: IProductDetail;
  onSelectProduct: (product: IProductDetail) => void;
  onSendMessage: (content: string) => void;
  onRefreshMessage?: (messageId: string) => void;
  onChangeVersion?: (messageId: string, direction: 'prev' | 'next') => void;
  isLoading?: boolean;
  currentSessionId?: string;
}

export function ChatArea({ currentSessionId, messages, selectedProduct, onSelectProduct: _onSelectProduct, onSendMessage, onRefreshMessage, onChangeVersion, isLoading = false }: ChatAreaProps) {
  const hasMessages = messages.length > 0;
  const [isCompareMode, setIsCompareMode] = useState(false);
  const [compareModels, setCompareModels] = useState<string[]>([]);
  const [showModelSelector, setShowModelSelector] = useState(false);
  const [dropdownSearchQuery, setDropdownSearchQuery] = useState("");
  const [dropdownActiveCategory, setDropdownActiveCategory] = useState("对话模型");
  // 每个模型独立的消息列表
  const [compareMessages, setCompareMessages] = useState<Record<string, Message[]>>({});
  // 每个模型独立的会话 ID
  const [compareSessionIds, setCompareSessionIds] = useState<Record<string, string>>({});
  // 模型列表（从 API 获取）
  const [modelList, setModelList] = useState<ModelData[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  // 分类列表（从 API 获取）
  const [categories, setCategories] = useState<Array<{ id: string; name: string }>>([]);
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  // 当前选中的模型ID（从product转换而来，用于兼容现有逻辑）
  const selectedModel = selectedProduct?.productId || "";

  // 模型选择处理（调用父组件的 onSelectProduct）
  const handleSelectModel = (modelId: string) => {
    const model = modelList.find(m => m.id === modelId);
    if (model && _onSelectProduct) {
      // 构造一个最小的 Product 对象
      _onSelectProduct({
        productId: model.id,
        name: model.name,
        description: model.description,
      } as IProductDetail);
    }
  };

  // 获取分类列表
  useEffect(() => {
    const fetchCategories = async () => {
      setCategoriesLoading(true);
      try {
        const response = await APIs.getCategoriesByProductType({ productType: "MODEL_API" });

        if (response.code === "SUCCESS" && response.data?.content) {
          const categoryList = response.data.content.map((cat) => ({
            id: cat.categoryId,
            name: cat.name,
          }));

          // 添加"全部"选项
          setCategories([
            { id: "all", name: "全部" },
            ...categoryList
          ]);

          // 设置默认选中"全部"
          setDropdownActiveCategory("all");
        }
      } catch (error) {
        console.error("Failed to fetch categories:", error);
        antdMessage.error("获取分类列表失败");
      } finally {
        setCategoriesLoading(false);
      }
    };

    fetchCategories();
  }, []);

  // 获取模型列表
  useEffect(() => {
    const fetchProducts = async () => {
      setModelsLoading(true);
      try {
        const response = await APIs.getProducts({
          type: "MODEL_API",
          page: 0,
          size: 100,
        });

        if (response.code === "SUCCESS" && response.data?.content) {
          const products: IProductDetail[] = response.data.content;

          // 转换为 ModelData 格式
          const models: ModelData[] = products.map(product => ({
            id: product.productId,
            name: product.name,
            description: product.description,
            category: product.modelConfig?.modelAPIConfig?.modelCategory || "对话模型",
            icon: getIconString(product.icon), // 使用产品的 icon 字段
            productCategories: product.categories.map(cat => cat.categoryId) || [], // 添加产品分类
          }));

          setModelList(models);
        }
      } catch (error) {
        console.error("Failed to fetch products:", error);
        antdMessage.error("获取模型列表失败");
      } finally {
        setModelsLoading(false);
      }
    };

    fetchProducts();
  }, []);

  const handleToggleCompare = () => {
    // 点击对比时，打开模型选择弹窗，当前模型会被默认选中
    setShowModelSelector(true);
  };

  const handleSelectModels = (models: string[]) => {
    if (isCompareMode) {
      // 对比模式下添加新模型，只添加不重复的
      const newModels = models.filter(m => !compareModels.includes(m));
      // 为新模型初始化空消息列表
      const newCompareMessages = { ...compareMessages };
      newModels.forEach(modelId => {
        newCompareMessages[modelId] = [];
      });
      setCompareMessages(newCompareMessages);
      setCompareModels([...compareModels, ...newModels]);
    } else {
      // 首次进入对比模式，将当前模型和选中的模型合并
      const allModels = [selectedModel, ...models];
      // 初始化消息列表：当前模型保留已有消息，其他模型为空
      const newCompareMessages: Record<string, Message[]> = {
        [selectedModel]: [...messages],
      };
      models.forEach(modelId => {
        newCompareMessages[modelId] = [];
      });
      setCompareMessages(newCompareMessages);
      setCompareModels(allModels);
      setIsCompareMode(true);
    }
    setShowModelSelector(false);
  };

  const handleCloseModel = (modelId: string) => {
    const newModels = compareModels.filter((id) => id !== modelId);
    if (newModels.length <= 1) {
      // 只剩一个或没有了，退出对比模式
      setIsCompareMode(false);
      setCompareModels([]);
      setCompareMessages({});
      setCompareSessionIds({}); // 清空所有会话ID
    } else {
      // 删除该模型的消息和会话ID
      const newCompareMessages = { ...compareMessages };
      delete newCompareMessages[modelId];
      setCompareMessages(newCompareMessages);

      const newSessionIds = { ...compareSessionIds };
      delete newSessionIds[modelId];
      setCompareSessionIds(newSessionIds);

      setCompareModels(newModels);
    }
  };

  const handleAddModel = () => {
    // 添加新的对比模型
    setShowModelSelector(true);
  };

  const handleSwitchModel = (index: number, newModelId: string) => {
    // 切换指定位置的模型
    const oldModelId = compareModels[index];
    const newModels = [...compareModels];
    newModels[index] = newModelId;
    setCompareModels(newModels);

    // 复制旧模型的用户消息到新模型（保留对话上下文）
    if (oldModelId && compareMessages[oldModelId]) {
      const userMessages = compareMessages[oldModelId].filter(msg => msg.role === "user");
      setCompareMessages(prev => ({
        ...prev,
        [newModelId]: userMessages
      }));
    }

    // 移除旧模型的会话ID（新模型会创建新会话）
    if (oldModelId) {
      const newSessionIds = { ...compareSessionIds };
      delete newSessionIds[oldModelId];
      setCompareSessionIds(newSessionIds);
    }
  };

  // 获取模型名称
  const getModelName = (modelId: string) => {
    const model = modelList.find(m => m.id === modelId);
    return model ? model.name : modelId;
  };

  const getModelIcon = (modelId: string) => {
    const model = modelList.find(m => m.id === modelId);
    return model ? model.icon : undefined;
  };

  // 处理对比模式下的消息发送
  const handleCompareSendMessage = async (content: string) => {
    const startTime = Date.now();
    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content,
      timestamp: new Date(),
    };

    // 为所有对比模型添加用户消息
    const newCompareMessages = { ...compareMessages };
    compareModels.forEach(modelId => {
      newCompareMessages[modelId] = [...(newCompareMessages[modelId] || []), userMessage];
    });
    setCompareMessages(newCompareMessages);
    const conversationId = generateConversationId();
    const questionId = generateQuestionId();
    let sessionId = currentSessionId;
    if (!sessionId) {
      // 获取或创建该模型的会话
      const res = await APIs.createSession({
        talkType: "MODEL",
        name: content.length > 20 ? content.substring(0, 20) + "..." : content,
        products: compareModels,
      });
      if (res.code === "SUCCESS") {
        sessionId = res.data.sessionId;
      } else {
        throw new Error("创建会话失败");
      }
    }
    const requests = compareModels.map(async (modelId) => {
      try {
        const messagePayload = {
          productId: modelId,
          sessionId,
          conversationId,
          questionId,
          question: content,
          stream: true,
          needMemory: true,
        };

        // 创建 loading 消息
        const assistantMessageId = `${Date.now()}-${modelId}`;
        let fullContent = '';
        let chatId = '';
        let firstTokenTime: number | undefined;

        // 添加空的 AI 消息用于显示 loading
        setCompareMessages(prev => ({
          ...prev,
          [modelId]: [...(prev[modelId] || []), {
            id: assistantMessageId,
            role: "assistant" as const,
            content: '',
            timestamp: new Date(),
          }]
        }));

        // 发送 SSE 请求
        const streamUrl = APIs.getChatMessageStreamUrl();
        const accessToken = localStorage.getItem('access_token');

        await handleSSEStream(
          streamUrl,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': accessToken ? `Bearer ${accessToken}` : '',
            },
            body: JSON.stringify(messagePayload),
          },
          {
            onStart: (id) => {
              chatId = id;
            },
            onChunk: (chunk) => {
              fullContent += chunk;
              // 第一个 chunk 到达，记录首字时间
              if (fullContent === chunk && firstTokenTime === undefined) {
                firstTokenTime = Date.now() - startTime;
              }
              // 更新消息内容
              setCompareMessages(prev => ({
                ...prev,
                [modelId]: (prev[modelId] || []).map(msg =>
                  msg.id === assistantMessageId
                    ? { ...msg, content: fullContent }
                    : msg
                )
              }));
            },
            onComplete: (content, _chatId, usage) => {
              const totalTime = Date.now() - startTime;
              setCompareMessages(prev => ({
                ...prev,
                [modelId]: (prev[modelId] || []).map(msg =>
                  msg.id === assistantMessageId
                    ? {
                      ...msg,
                      id: chatId || assistantMessageId,
                      content: content,
                      firstTokenTime: firstTokenTime ? Math.round(firstTokenTime) : undefined,
                      totalTime: usage?.elapsed_time || Math.round(totalTime),
                      inputTokens: usage?.prompt_tokens,
                      outputTokens: usage?.completion_tokens,
                    }
                    : msg
                )
              }));
            },
            onError: (error) => {
              console.error(`Model ${modelId} error:`, error);
              setCompareMessages(prev => ({
                ...prev,
                [modelId]: (prev[modelId] || []).map(msg =>
                  msg.id === assistantMessageId
                    ? {
                      ...msg,
                      content: '',
                      error: true,
                      errorMessage: '网络异常，请重试',
                    }
                    : msg
                )
              }));
            },
          }
        );
      } catch (error) {
        console.error(`Failed to send message to model ${modelId}:`, error);
        antdMessage.error(`模型 ${getModelName(modelId)} 请求失败`);
      }
    });

    // 等待所有请求完成
    await Promise.allSettled(requests);
  };

  // 渲染模型选择浮层（与 ModelSelector 保持一致）
  const renderModelDropdown = (currentModelId: string, onSelect: (modelId: string) => void) => {
    const filteredModels = modelList.filter(model => {
      // 分类过滤：如果选择"全部"或模型的 productCategories 包含当前选中的分类
      const matchesCategory = dropdownActiveCategory === "all" ||
        model.productCategories.includes(dropdownActiveCategory);

      // 搜索过滤
      const matchesSearch = dropdownSearchQuery === "" ||
        model.name.toLowerCase().includes(dropdownSearchQuery.toLowerCase()) ||
        model.description.toLowerCase().includes(dropdownSearchQuery.toLowerCase());

      return matchesCategory && matchesSearch;
    });

    return (
      <div className="bg-white rounded-lg shadow-xl border border-gray-200 w-[420px] max-h-[500px] flex flex-col">
        {/* 搜索框 */}
        <div className="p-4 pb-3">
          <Input
            prefix={<SearchOutlined className="text-gray-400" />}
            placeholder="搜索模型..."
            value={dropdownSearchQuery}
            onChange={(e) => setDropdownSearchQuery(e.target.value)}
            className="rounded-lg"
            allowClear
          />
        </div>

        {/* Tab 分类 */}
        <div className="px-4">
          {categoriesLoading ? (
            <div className="flex items-center justify-center py-4">
              <span className="text-gray-400 text-sm">加载分类中...</span>
            </div>
          ) : (
            <Tabs
              activeKey={dropdownActiveCategory}
              onChange={setDropdownActiveCategory}
              items={categories.map(category => ({
                key: category.id,
                label: category.name,
              }))}
            />
          )}
        </div>

        {/* 模型列表 */}
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          <div className="space-y-1">
            {filteredModels.map(model => (
              <div
                key={model.id}
                onClick={() => {
                  onSelect(model.id);
                  setDropdownSearchQuery("");
                }}
                className={`
                  px-3 py-2.5 rounded-lg cursor-pointer
                  flex items-center gap-3
                  transition-all duration-200
                  hover:bg-gray-50 hover:scale-[1.01]
                  ${model.id === currentModelId
                    ? "bg-colorPrimary/10 text-colorPrimary"
                    : "text-gray-700 hover:text-gray-900"
                  }
                `}
              >
                <ProductIconRenderer iconType={model.icon} className="w-5 h-5" />
                <span className="font-medium flex-1">{model.name}</span>
                {model.id === currentModelId && (
                  <CheckOutlined className="text-colorPrimary text-xs" />
                )}
              </div>
            ))}
            {filteredModels.length === 0 && (
              <div className="text-center py-8 text-gray-400">
                未找到匹配的模型
              </div>
            )}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="flex-1 flex flex-col h-full">
      {/* 顶部栏：模型选择器 + 多模型对比按钮（仅在单模型模式显示） */}
      {!isCompareMode && (
        <div className="flex items-center gap-4 px-4 py-4">
          <ModelSelector
            selectedModel={selectedModel}
            onSelectModel={handleSelectModel}
            modelList={modelList}
            loading={modelsLoading}
            categories={categories}
            categoriesLoading={categoriesLoading}
          />

          {/* 分割线 */}
          <div className="h-6 w-px bg-gray-300"></div>

          <button
            onClick={handleToggleCompare}
            className="
              flex items-center gap-2 px-4 py-2 rounded-full
              border border-gray-300 text-gray-600
              bg-transparent
              hover:border-colorPrimary hover:text-colorPrimary
              transition-all duration-300
            "
          >
            <AppstoreAddOutlined />
            <span className="text-sm font-medium">多模型对比</span>
          </button>
        </div>
      )}

      {/* 多模型选择弹窗 */}
      {showModelSelector && (
        <MultiModelSelector
          currentModel={isCompareMode ? compareModels[0] : selectedModel}
          excludeModels={isCompareMode ? compareModels : []}
          onConfirm={handleSelectModels}
          onCancel={() => setShowModelSelector(false)}
          modelList={modelList}
          loading={modelsLoading}
        />
      )}

      {/* 主要内容区域 */}
      <div className="flex-1 overflow-hidden">
        {isCompareMode ? (
          /* 多模型对比视图 - 使用分割线 */
          <div className="h-full flex">
            {compareModels.map((modelId, index) => (
              <div
                key={`${modelId}-${index}`}
                className={`flex-1 flex flex-col ${index < compareModels.length - 1 ? 'border-r border-gray-200' : ''}`}
              >
                {/* 模型名称标题（可切换） + 关闭按钮 + 添加按钮 */}
                <div className="px-4 py-3 flex items-center justify-between border-b border-gray-200">
                  <Dropdown
                    popupRender={() => renderModelDropdown(modelId, (newModelId) => handleSwitchModel(index, newModelId))}
                    trigger={['click']}
                    placement="bottomLeft"
                  >
                    <button className="flex items-center gap-1 text-sm font-semibold text-gray-900 hover:text-colorPrimary transition-colors">
                      {getModelName(modelId)}
                      <DownOutlined className="text-xs text-gray-400" />
                    </button>
                  </Dropdown>
                  <div className="flex items-center gap-2">
                    {index === 1 && compareModels.length < 3 && (
                      <button
                        onClick={handleAddModel}
                        className="text-gray-400 hover:text-colorPrimary transition-colors duration-200"
                        title="添加对比模型"
                      >
                        <PlusOutlined className="text-xs" />
                      </button>
                    )}
                    <button
                      onClick={() => handleCloseModel(modelId)}
                      className="text-gray-400 hover:text-gray-600 transition-colors duration-200"
                    >
                      <CloseOutlined className="text-xs" />
                    </button>
                  </div>
                </div>

                {/* 消息列表 */}
                <div className="flex-1 overflow-y-auto px-4 py-4">
                  {!(compareMessages[modelId]?.length > 0) ? (
                    <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                    </div>
                  ) : (
                    <MessageList
                      messages={compareMessages[modelId] || []}
                      modelName={getModelName(modelId)}
                      modelIcon={getModelIcon(modelId)}
                    />
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          /* 单模型视图 */
          <div className="h-full overflow-y-auto">
            {!hasMessages ? (
              /* 欢迎页面 */
              <div className="flex flex-col items-center justify-center h-full px-4">
                <div className="max-w-3xl w-full">
                  {/* 欢迎标题 */}
                  <div className="text-center mb-12">
                    <h1 className="text-2xl font-medium text-gray-900 mb-2">
                      您好，欢迎来到 <span className="text-colorPrimary">Himarket 体验中心_</span>
                    </h1>
                  </div>

                  {/* 输入框 */}
                  <div className="mb-8">
                    <InputBox onSendMessage={onSendMessage} isLoading={isLoading} />
                  </div>

                  {/* 推荐问题 */}
                  <SuggestedQuestions onSelectQuestion={onSendMessage} />
                </div>
              </div>
            ) : (
              /* 聊天消息列表 */
              <MessageList
                messages={messages}
                modelName={getModelName(selectedModel)}
                modelIcon={getModelIcon(selectedModel)}
                onRefresh={onRefreshMessage}
                onChangeVersion={onChangeVersion}
                isLoading={isLoading}
              />
            )}
          </div>
        )}
      </div>

      {/* 底部输入框（单模型有消息时显示，或对比模式下始终显示） */}
      {((!isCompareMode && hasMessages) || isCompareMode) && (
        <div className="p-4 pb-0">
          <div className="max-w-3xl mx-auto">
            <InputBox onSendMessage={isCompareMode ? handleCompareSendMessage : onSendMessage} isLoading={isLoading} />
          </div>
        </div>
      )}
    </div>
  );
}
