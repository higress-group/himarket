import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import { Layout } from "../components/Layout";
import { Sidebar } from "../components/chat/Sidebar";
import { ChatArea } from "../components/chat/ChatArea";
import {
  type Product,
  createSession,
  sendChatMessage,
  getConversations,
  getChatMessageStreamUrl,
  type Conversation,
} from "../lib/api";
import { generateConversationId, generateQuestionId } from "../lib/uuid";
import { handleSSEStream } from "../lib/sse";

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
}

function Chat() {
  const location = useLocation();
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [currentConversationId, setCurrentConversationId] = useState<string | null>(null);
  const [useStream, _setUseStream] = useState(true); // 默认使用流式响应

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
          productIds: [selectedProduct.productId],
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

      // 发送消息（sessionId 已确保不为 null）
      if (!sessionId) {
        throw new Error("会话ID不存在");
      }

      const messagePayload = {
        conversationId,
        questionId,
        answerIndex: 0,
        question: content,
        stream: useStream,
        needMemory: true,
      };

      if (useStream) {
        // 流式响应处理
        const assistantMessageId = `${Date.now()}-assistant`;
        let fullContent = '';
        let chatId = '';

        // 先添加一个空的 AI 消息
        setMessages(prev => [...prev, {
          id: assistantMessageId,
          role: "assistant",
          content: '',
          timestamp: new Date(),
        }]);

        const streamUrl = getChatMessageStreamUrl(sessionId);
        const tempToken = (import.meta as any).env.VITE_TEMP_AUTH_TOKEN;
        const accessToken = localStorage.getItem('access_token');

        await handleSSEStream(
          streamUrl,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              // 临时 token 已包含 Bearer 前缀，直接使用；生产环境 token 需要添加 Bearer 前缀
              'Authorization': tempToken || (accessToken ? `Bearer ${accessToken}` : ''),
            },
            body: JSON.stringify(messagePayload),
          },
          {
            onStart: (id) => {
              chatId = id;
            },
            onChunk: (chunk) => {
              fullContent += chunk;
              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? { ...msg, content: fullContent }
                  : msg
              ));
            },
            onComplete: (content) => {
              const totalTime = Date.now() - startTime;
              setMessages(prev => prev.map(msg =>
                msg.id === assistantMessageId
                  ? {
                      ...msg,
                      id: chatId || assistantMessageId,
                      content: content,
                      totalTime: Math.round(totalTime),
                      inputTokens: Math.round(messagePayload.question.length * 1.5),
                      outputTokens: Math.round(content.length * 1.5),
                    }
                  : msg
              ));
            },
            onError: (error) => {
              antdMessage.error(`发送消息失败: ${error}`);
              // 移除空的 AI 消息
              setMessages(prev => prev.filter(msg => msg.id !== assistantMessageId));
            },
          }
        );
      } else {
        // 非流式响应处理
        const chatResponse: any = await sendChatMessage(sessionId, messagePayload);

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
        } else {
          throw new Error("获取回复失败");
        }
      }
    } catch (error) {
      console.error("Failed to send message:", error);
      antdMessage.error("发送消息失败");

      // 移除用户消息
      setMessages(prev => prev.filter(msg => msg.id !== userMessage.id));
    }
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
  const handleSelectSession = async (sessionId: string) => {
    try {
      setCurrentSessionId(sessionId);
      setMessages([]); // 先清空消息

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
      <div className="flex flex-1 h-full bg-transparent">
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
        />
      </div>
    </Layout>
  );
}

export default Chat;
