import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import { Layout } from "../components/Layout";
import { Sidebar } from "../components/chat/Sidebar";
import { ChatArea } from "../components/chat/ChatArea";
import api, {
  type Product,
  createSession,
  sendChatMessage,
  getConversations,
  getChatMessageStreamUrl,
  type Conversation,
} from "../lib/api";
import { generateConversationId, generateQuestionId } from "../lib/uuid";
import { handleSSEStream } from "../lib/sse";

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
}

function Chat() {
  const location = useLocation();
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [useStream, _setUseStream] = useState(true); // 默认使用流式响应
  // 记录每个 questionId 对应的问题内容，用于重新生成
  const [questionContentMap, setQuestionContentMap] = useState<Map<string, string>>(new Map());
  const [isLoading, setIsLoading] = useState(false); // 添加loading状态

  // 从 location.state 接收选中的产品
  useEffect(() => {
    const state = location.state as { selectedProduct?: Product } | null;
    if (state?.selectedProduct) {
      setSelectedProduct(state.selectedProduct);
      // 清除 location.state，避免刷新后重复应用
      window.history.replaceState({}, document.title);
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
        const sessionResponse: any = await createSession({
          talkType: "MODEL",
          name: content.substring(0, 20) + "...", // 使用问题前20个字符作为会话名称
          products: [selectedProduct.productId],
        });

        if (sessionResponse.code === "SUCCESS") {
          sessionId = sessionResponse.data.sessionId;
          setCurrentSessionId(sessionId);
        } else {
          throw new Error("创建会话失败");
        }
      }

      // 生成或使用现有的 conversationId 和 questionId
      const conversationId = currentConversationId || generateConversationId();
      const questionId = generateQuestionId();

      if (!currentConversationId) {
        setCurrentConversationId(conversationId);
      }

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

        const streamUrl = getChatMessageStreamUrl();
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
              // 更新消息内容
              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? { ...msg, content: fullContent }
                  : msg
              ));
              // 第一个 chunk 到达，关闭 loading 状态
              if (fullContent === chunk) {
                setIsLoading(false);
              }
            },
            onComplete: (content) => {
              const totalTime = Date.now() - startTime;
              const newVersion: MessageVersion = {
                content: content,
                totalTime: Math.round(totalTime),
                inputTokens: Math.round(messagePayload.question.length * 1.5),
                outputTokens: Math.round(content.length * 1.5),
              };

              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? {
                      ...msg,
                      id: chatId || assistantMessageId,
                      content: content,
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
            onError: (error) => {
              antdMessage.error(`发送消息失败: ${error}`);
              // 移除空的 AI 消息
              setMessages(prev => prev.filter(msg => msg.id !== assistantMessageId));
              setIsLoading(false); // 错误时也要重置loading
            },
          }
        );
      } else {
        // 非流式响应处理
        const chatResponse: any = await sendChatMessage(messagePayload);

        // 处理响应
        if (chatResponse.success && chatResponse.answer) {
          const assistantMessage: Message = {
            id: chatResponse.chatId || Date.now().toString(),
            role: "assistant",
            content: chatResponse.answer,
            timestamp: new Date(),
            firstTokenTime: Math.round(Math.random() * 500), // TODO: 从响应中获取
            totalTime: Math.round(Date.now() - startTime),
            inputTokens: Math.round(content.length * 1.5), // TODO: 从响应中获取
            outputTokens: Math.round(chatResponse.answer.length * 1.5), // TODO: 从响应中获取
          };
          setMessages(prev => [...prev, assistantMessage]);

          setIsLoading(false); // 完成loading
        } else {
          setIsLoading(false); // 失败时也要重置
          throw new Error("获取回复失败");
        }
      }
    } catch (error) {
      console.error("Failed to send message:", error);
      antdMessage.error("发送消息失败");

      // 移除用户消息
      setMessages(prev => prev.filter(msg => msg.id !== userMessage.id));
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

        // 先显示 loading 状态
        setMessages(prev => prev.map(msg =>
          msg.id === messageId
            ? { ...msg, content: '' }
            : msg
        ));

        const streamUrl = getChatMessageStreamUrl();
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
              setMessages(prev => prev.map(msg =>
                msg.id === messageId
                  ? { ...msg, content: fullContent }
                  : msg
              ));
              if (fullContent === chunk) {
                setIsLoading(false);
              }
            },
            onComplete: (content) => {
              const totalTime = Date.now() - startTime;
              const newVersion: MessageVersion = {
                content: content,
                totalTime: Math.round(totalTime),
                inputTokens: Math.round(questionContent.length * 1.5),
                outputTokens: Math.round(content.length * 1.5),
              };

              setMessages(prev => prev.map(msg => {
                if (msg.id === messageId) {
                  const updatedVersions = [...(msg.versions || []), newVersion];
                  const newIndex = updatedVersions.length - 1;
                  return {
                    ...msg,
                    content: newVersion.content,
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
            onError: (error) => {
              antdMessage.error(`重新生成失败: ${error}`);
              setIsLoading(false);
            },
          }
        );
      }
    } catch (error) {
      console.error("Failed to refresh message:", error);
      antdMessage.error("重新生成失败");
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
  };

  const handleSelectProduct = (product: Product) => {
    setSelectedProduct(product);
  };

  // 加载会话的历史聊天记录
  const handleSelectSession = async (sessionId: string, productIds: string[]) => {
    try {
      setCurrentSessionId(sessionId);
      setMessages([]); // 先清空消息

      // 根据 productIds 加载产品信息
      if (productIds && productIds.length > 0) {
        try {
          // 加载第一个产品作为选中产品（TODO: 支持多模型对比）
          const productResponse: any = await api.get(`/products/${productIds[0]}`);
          console.log('Product response:', productResponse);
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

      const response: any = await getConversations(sessionId);

      if (response.code === "SUCCESS" && response.data) {
        const conversations: Conversation[] = response.data;

        // 将会话记录转换为消息列表
        const allMessages: Message[] = [];

        conversations.forEach(conversation => {
          conversation.questions.forEach(question => {
            // 添加用户消息
            allMessages.push({
              id: question.questionId,
              role: "user",
              content: question.content,
              timestamp: new Date(),
            });

            // 添加 AI 回复（取最后一轮的第一个结果）
            if (question.answers.length > 0) {
              const lastAnswer = question.answers[question.answers.length - 1];
              if (lastAnswer.results.length > 0) {
                const result = lastAnswer.results[0];
                allMessages.push({
                  id: result.answerId,
                  role: "assistant",
                  content: result.content,
                  timestamp: new Date(),
                });
              }
            }
          });
        });

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

  return (
    <Layout>
      <div className="flex h-[calc(100vh-92px)] bg-transparent">
        <Sidebar
          currentSessionId={currentSessionId}
          onNewChat={handleNewChat}
          onSelectSession={handleSelectSession}
        />
        <ChatArea
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
