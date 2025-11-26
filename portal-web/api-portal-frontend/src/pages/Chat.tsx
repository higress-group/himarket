import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import { Layout } from "../components/Layout";
import { Sidebar } from "../components/chat/Sidebar";
import { ChatArea } from "../components/chat/ChatArea";
import { generateConversationId, generateQuestionId } from "../lib/uuid";
import { handleSSEStream } from "../lib/sse";
import APIs, { type IConversation, type IProductDetail } from "../lib/apis";

interface MessageVersion {
  content: string;
  firstTokenTime?: number;
  totalTime?: number;
  inputTokens?: number;
  outputTokens?: number;
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
  // 多版本答案支持（仅 assistant 消息）
  questionId?: string; // 关联的问题ID
  versions?: MessageVersion[]; // 所有版本的答案
  currentVersionIndex?: number; // 当前显示的版本索引（0-based）
  // 错误状态
  error?: boolean; // 是否是错误消息
  errorMessage?: string; // 错误提示文本
}

function Chat() {
  const location = useLocation();
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<IProductDetail>();
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [useStream] = useState(true); // 默认使用流式响应
  // 记录每个 questionId 对应的问题内容，用于重新生成
  const [questionContentMap, setQuestionContentMap] = useState<Map<string, string>>(new Map());
  const [isLoading, setIsLoading] = useState(false); // 添加loading状态
  const [sidebarRefreshTrigger, setSidebarRefreshTrigger] = useState(0); // 用于触发 Sidebar 刷新

  // 从 location.state 接收选中的产品，或者加载默认第一个模型
  useEffect(() => {
    const state = location.state as { selectedProduct?: IProductDetail } | null;
    if (state?.selectedProduct) {
      setSelectedProduct(state.selectedProduct);
      // 清除 location.state，避免刷新后重复应用
      window.history.replaceState({}, document.title);
    } else {
      // 如果没有选中的产品，自动加载并选择第一个模型
      const loadDefaultModel = async () => {
        try {
          const response = await APIs.getProducts({
            type: "MODEL_API",
            page: 0,
            size: 1,
          });
          if (response.code === "SUCCESS" && response.data?.content?.length > 0) {
            setSelectedProduct(response.data.content[0]);
          }
        } catch (error) {
          console.error("Failed to load default model:", error);
        }
      };
      loadDefaultModel();
    }
  }, [location]);

  const handleSendMessage = async (content: string) => {
    if (!selectedProduct) {
      antdMessage.error("请先选择一个模型");
      return;
    }

    setIsLoading(true); // 开始loading
    const startTime = Date.now();
    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMessage]);

    try {
      // 如果没有会话，先创建会话
      let sessionId = currentSessionId;
      if (!sessionId) {
        const sessionResponse = await APIs.createSession({
          talkType: "MODEL",
          name: content.length > 20 ? content.substring(0, 20) + "..." : content, // 只在超过20个字符时添加省略号
          products: [selectedProduct.productId],
        });

        if (sessionResponse.code === "SUCCESS") {
          sessionId = sessionResponse.data.sessionId;
          setCurrentSessionId(sessionId);
          // 触发 Sidebar 刷新以显示新会话
          setSidebarRefreshTrigger(prev => prev + 1);
        } else {
          throw new Error("创建会话失败");
        }
      }

      // 每次新问答都生成新的 conversationId 和 questionId
      const conversationId = generateConversationId();
      const questionId = generateQuestionId();

      // 更新当前的 conversationId
      setCurrentConversationId(conversationId);

      // 保存问题内容，用于重新生成
      setQuestionContentMap(prev => {
        const newMap = new Map(prev);
        newMap.set(questionId, content);
        return newMap;
      });

      // 发送消息（sessionId 已确保不为 null）
      if (!sessionId) {
        throw new Error("会话ID不存在");
      }

      const messagePayload = {
        productId: selectedProduct.productId,
        sessionId,
        conversationId,
        questionId,
        question: content,
        stream: useStream,
        needMemory: true,
      };

      if (useStream) {
        // 流式响应处理
        const assistantMessageId = `${Date.now()}-assistant`;
        let fullContent = '';
        let chatId = '';
        let firstTokenTime: number | undefined;

        // 立即添加一个空的 AI 消息框用于显示 loading
        setMessages(prev => [...prev, {
          id: assistantMessageId,
          role: "assistant",
          content: '',
          timestamp: new Date(),
          questionId, // 关联问题ID
          versions: [], // 初始化版本数组
          currentVersionIndex: 0, // 当前版本索引
        }]);

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
                setIsLoading(false);
              }
              // 更新消息内容
              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? { ...msg, content: fullContent }
                  : msg
              ));
            },
            onComplete: (content, _chatId, usage) => {
              const totalTime = Date.now() - startTime;
              const newVersion: MessageVersion = {
                content: content,
                firstTokenTime: firstTokenTime ? Math.round(firstTokenTime) : undefined,
                totalTime: usage?.elapsed_time || Math.round(totalTime),
                inputTokens: usage?.prompt_tokens,
                outputTokens: usage?.completion_tokens,
              };

              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? {
                    ...msg,
                    id: chatId || assistantMessageId,
                    content: content,
                    firstTokenTime: newVersion.firstTokenTime,
                    totalTime: newVersion.totalTime,
                    inputTokens: newVersion.inputTokens,
                    outputTokens: newVersion.outputTokens,
                    versions: [newVersion], // 添加到版本数组
                    currentVersionIndex: 0,
                  }
                  : msg
              ));

              setIsLoading(false); // 完成loading
            },
            onError: () => {
              // 不再移除消息，而是更新为错误状态
              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? {
                    ...msg,
                    content: '',
                    error: true,
                    errorMessage: '网络异常，请重试',
                  }
                  : msg
              ));
              setIsLoading(false); // 错误时也要重置loading
            },
          }
        );
      }
    } catch (error) {
      console.error("Failed to send message:", error);

      // 查找并更新最后一条 AI 消息为错误状态（如果还没有被标记为错误）
      setMessages(prev => {
        const lastMsg = prev[prev.length - 1];
        // 如果最后一条消息是 assistant，更新它为错误状态（避免添加新消息）
        if (lastMsg && lastMsg.role === "assistant") {
          // 如果已经是错误状态，不再重复更新
          if (lastMsg.error) {
            return prev;
          }
          return prev.map((msg, idx) =>
            idx === prev.length - 1
              ? {
                ...msg,
                content: '',
                error: true,
                errorMessage: '网络异常，请重试',
              }
              : msg
          );
        }
        // 如果最后一条不是 assistant 消息，添加一个错误消息（这种情况很少见）
        return [...prev, {
          id: `${Date.now()}-error`,
          role: "assistant" as const,
          content: '',
          timestamp: new Date(),
          error: true,
          errorMessage: '网络异常，请重试',
        }];
      });
      setIsLoading(false); // 错误时重置loading
    }
  };

  // 重新生成答案
  const handleRefreshMessage = async (messageId: string) => {
    if (!selectedProduct || !currentSessionId) {
      antdMessage.error("无法重新生成，缺少必要信息");
      return;
    }

    // 找到要重新生成的消息
    const message = messages.find(msg => msg.id === messageId);
    if (!message || message.role !== "assistant" || !message.questionId) {
      antdMessage.error("无法找到对应的问题");
      return;
    }

    const questionId = message.questionId;
    const questionContent = questionContentMap.get(questionId);
    if (!questionContent) {
      antdMessage.error("无法找到原始问题内容");
      return;
    }

    setIsLoading(true);
    const startTime = Date.now();

    try {
      const conversationId = currentConversationId || generateConversationId();

      const messagePayload = {
        productId: selectedProduct.productId,
        sessionId: currentSessionId,
        conversationId,
        questionId,
        question: questionContent,
        stream: useStream,
        needMemory: true,
      };

      if (useStream) {
        let fullContent = '';
        let firstTokenTime: number | undefined;

        // 先显示 loading 状态（清空内容和错误状态）
        setMessages(prev => prev.map(msg =>
          msg.id === messageId
            ? { ...msg, content: '', error: undefined, errorMessage: undefined }
            : msg
        ));

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
            onChunk: (chunk) => {
              fullContent += chunk;
              // 第一个 chunk 到达，记录首字时间
              if (fullContent === chunk && firstTokenTime === undefined) {
                firstTokenTime = Date.now() - startTime;
                setIsLoading(false);
              }
              setMessages(prev => prev.map(msg =>
                msg.id === messageId
                  ? { ...msg, content: fullContent }
                  : msg
              ));
            },
            onComplete: (content, _chatId, usage) => {
              const totalTime = Date.now() - startTime;
              const newVersion: MessageVersion = {
                content: content,
                firstTokenTime: firstTokenTime ? Math.round(firstTokenTime) : undefined,
                totalTime: usage?.elapsed_time || Math.round(totalTime),
                inputTokens: usage?.prompt_tokens,
                outputTokens: usage?.completion_tokens,
              };

              setMessages(prev => prev.map(msg => {
                if (msg.id === messageId) {
                  const updatedVersions = [...(msg.versions || []), newVersion];
                  const newIndex = updatedVersions.length - 1;
                  return {
                    ...msg,
                    content: newVersion.content,
                    firstTokenTime: newVersion.firstTokenTime,
                    totalTime: newVersion.totalTime,
                    inputTokens: newVersion.inputTokens,
                    outputTokens: newVersion.outputTokens,
                    versions: updatedVersions,
                    currentVersionIndex: newIndex,
                  };
                }
                return msg;
              }));

              setIsLoading(false);
            },
            onError: () => {
              // 更新消息为错误状态，而不是只显示 toast
              setMessages(prev => prev.map(msg =>
                msg.id === messageId
                  ? {
                    ...msg,
                    content: '',
                    error: true,
                    errorMessage: '重新生成失败，请重试',
                  }
                  : msg
              ));
              setIsLoading(false);
            },
          }
        );
      }
    } catch (error) {
      console.error("Failed to refresh message:", error);
      // 更新消息为错误状态（避免重复更新）
      setMessages(prev => prev.map(msg =>
        msg.id === messageId && !msg.error // 只在还不是错误状态时更新
          ? {
            ...msg,
            content: '',
            error: true,
            errorMessage: '重新生成失败，请重试',
          }
          : msg
      ));
      setIsLoading(false);
    }
  };

  // 切换答案版本
  const handleChangeVersion = (messageId: string, direction: 'prev' | 'next') => {
    setMessages(prev => prev.map(msg => {
      if (msg.id === messageId && msg.versions && msg.versions.length > 1) {
        const currentIndex = msg.currentVersionIndex ?? 0;
        let newIndex = currentIndex;

        if (direction === 'prev' && currentIndex > 0) {
          newIndex = currentIndex - 1;
        } else if (direction === 'next' && currentIndex < msg.versions.length - 1) {
          newIndex = currentIndex + 1;
        }

        if (newIndex !== currentIndex) {
          const selectedVersion = msg.versions[newIndex];
          return {
            ...msg,
            content: selectedVersion.content,
            firstTokenTime: selectedVersion.firstTokenTime,
            totalTime: selectedVersion.totalTime,
            inputTokens: selectedVersion.inputTokens,
            outputTokens: selectedVersion.outputTokens,
            currentVersionIndex: newIndex,
          };
        }
      }
      return msg;
    }));
  };

  const handleNewChat = () => {
    setMessages([]);
    setCurrentSessionId(null);
    setCurrentConversationId(null);
    setQuestionContentMap(new Map()); // 清空问题内容映射
  };

  const handleSelectProduct = (product: IProductDetail) => {
    setSelectedProduct(product);
  };

  // 加载会话的历史聊天记录
  const handleSelectSession = async (sessionId: string, productIds: string[]) => {
    // 如果点击的是当前已选中的会话，不重复加载
    if (currentSessionId === sessionId) {
      return;
    }

    try {
      setCurrentSessionId(sessionId);
      // 不要立即清空消息，避免闪烁

      // 根据 productIds 加载产品信息
      if (productIds && productIds.length > 0) {
        try {
          // 加载第一个产品作为选中产品（TODO: 支持多模型对比）
          const productResponse = await APIs.getProduct({ id: productIds[0] });
          if (productResponse.code === "SUCCESS" && productResponse.data) {
            setSelectedProduct(productResponse.data);
          } else {
            console.error("Failed to load product: Invalid response", productResponse);
            antdMessage.error("加载模型信息失败");
          }
        } catch (error) {
          console.error("Failed to load product:", error);
          antdMessage.error("加载模型信息失败，请重新选择模型");
        }
      } else {
        console.warn("No productIds in session");
        antdMessage.warning("该会话没有关联的模型，请先选择模型");
      }

      const response = await APIs.getConversations(sessionId);

      if (response.code === "SUCCESS" && response.data) {
        const conversations: IConversation[] = response.data;

        // 将会话记录转换为消息列表
        const allMessages: Message[] = [];
        const newQuestionContentMap = new Map<string, string>();

        conversations.forEach(conversation => {
          conversation.questions.forEach(question => {
            // 添加用户消息
            allMessages.push({
              id: question.questionId,
              role: "user",
              content: question.content,
              timestamp: new Date(),
            });

            // 保存问题内容到 map，用于重新生成
            newQuestionContentMap.set(question.questionId, question.content);

            // 添加 AI 回复，支持多版本答案
            if (question.answers.length > 0) {
              // 将所有 answers 转换为 versions
              const versions: MessageVersion[] = question.answers
                .filter(answer => answer.results.length > 0)
                .map(answer => {
                  const result = answer.results[0]; // 取第一个 result（多模型对比场景）
                  return {
                    content: result.content,
                    totalTime: result.usage?.elapsed_time,
                    inputTokens: result.usage?.prompt_tokens,
                    outputTokens: result.usage?.completion_tokens,
                  };
                });

              if (versions.length > 0) {
                // 显示最后一个版本（用户最后看到的）
                const currentVersionIndex = versions.length - 1;
                const currentVersion = versions[currentVersionIndex];
                const lastAnswer = question.answers[question.answers.length - 1];
                const lastResult = lastAnswer.results[0];

                allMessages.push({
                  id: lastResult.answerId,
                  role: "assistant",
                  content: currentVersion.content,
                  timestamp: new Date(),
                  questionId: question.questionId, // 关联问题ID，用于重新生成
                  versions: versions, // 保存所有版本
                  currentVersionIndex: currentVersionIndex, // 当前显示的版本索引
                  // 从当前版本获取统计信息
                  totalTime: currentVersion.totalTime,
                  inputTokens: currentVersion.inputTokens,
                  outputTokens: currentVersion.outputTokens,
                });
              }
            }
          });
        });

        // 更新问题内容映射
        setQuestionContentMap(newQuestionContentMap);

        // 数据加载完成后再更新消息列表，避免闪烁
        setMessages(allMessages);

        // 设置当前的 conversationId（使用最后一个对话的 ID）
        if (conversations.length > 0) {
          setCurrentConversationId(conversations[conversations.length - 1].conversationId);
        }
      }
    } catch (error) {
      console.error("Failed to load conversation:", error);
      antdMessage.error("加载聊天记录失败");
    }
  };

  console.log(questionContentMap, 'asd...0')
  console.log(messages, 'asd...1')
  return (
    <Layout>
      <div className="flex h-[calc(100vh-96px)] bg-transparent">
        <Sidebar
          currentSessionId={currentSessionId}
          onNewChat={handleNewChat}
          onSelectSession={handleSelectSession}
          refreshTrigger={sidebarRefreshTrigger}
        />
        <ChatArea
          currentSessionId={currentSessionId}
          messages={messages}
          selectedProduct={selectedProduct}
          onSelectProduct={handleSelectProduct}
          onSendMessage={handleSendMessage}
          onRefreshMessage={handleRefreshMessage}
          onChangeVersion={handleChangeVersion}
          isLoading={isLoading}
        />
      </div>
    </Layout>
  );
}

export default Chat;
