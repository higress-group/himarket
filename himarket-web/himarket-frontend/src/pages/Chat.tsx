import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import { Layout } from "../components/Layout";
import { Sidebar } from "../components/chat/Sidebar";
import { WelcomeView } from "../components/WelcomeView";
import { LoginPrompt } from "../components/LoginPrompt";
import { useAuth } from "../hooks/useAuth";
import { useChatSession } from "../hooks/useChatSession";
import APIs, { type IProductDetail, type IAttachment } from "../lib/apis";
import { ChatArea } from "../components/chat/Area";


function Chat() {
  const location = useLocation();
  const { isLoggedIn } = useAuth();
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [selectedModel, setSelectedModel] = useState<IProductDetail>();
  const [chatType, setChatType] = useState<"TEXT" | "Image">("TEXT");

  const {
    modelConversation,
    generating,
    isMcpExecuting,
    currentSessionId,
    sidebarRefreshTrigger,
    sendMessage,
    regenerateMessage,
    handleStop,
    handleNewChat,
    handleSelectSession,
    onChangeActiveAnswer,
    addModels,
    closeModel,
  } = useChatSession();

  // 从 location.state 接收选中的产品，或者加载默认第一个模型
  useEffect(() => {
    if (!isLoggedIn) return;

    const state = location.state as { selectedProduct?: IProductDetail } | null;
    if (state?.selectedProduct) {
      setSelectedModel(state.selectedProduct);
      window.history.replaceState({}, document.title);
    } else {
      const loadDefaultModel = async () => {
        try {
          const response = await APIs.getProducts({
            type: "MODEL_API",
            page: 0,
            size: 1,
            ["modelFilter.category"]: chatType,
          });
          if (response.code === "SUCCESS" && response.data?.content?.length > 0) {
            setSelectedModel(response.data.content[0]);
          } else {
            setSelectedModel(undefined);
          }
        } catch (error) {
          console.error("Failed to load default model:", error);
        }
      };
      loadDefaultModel();
    }
  }, [location, chatType, isLoggedIn]);

  const handleSendMessage = async (
    content: string,
    mcps: IProductDetail[],
    enableWebSearch: boolean,
    modelMap: Map<string, IProductDetail>,
    attachments: IAttachment[] = [],
  ) => {
    if (!selectedModel) {
      antdMessage.error("请先选择一个模型");
      return;
    }
    await sendMessage(content, mcps, enableWebSearch, modelMap, selectedModel, attachments);
  };

  const handleGenerateMessage = async (params: {
    modelId: string; conversationId: string; questionId: string; content: string;
    mcps: IProductDetail[]; enableWebSearch: boolean; modelMap: Map<string, IProductDetail>;
    attachments?: IAttachment[];
  }) => {
    await regenerateMessage(params);
  };

  const handleSelectProduct = (product: IProductDetail) => {
    setSelectedModel(product);
    handleNewChat();
  };

  const handleAddModels = (modelIds: string[]) => {
    addModels(modelIds, selectedModel?.productId);
  };

  return (
    <Layout>
      {!isLoggedIn ? (
        <>
          <WelcomeView type="chat" />
          <LoginPrompt
            open={loginPromptOpen}
            onClose={() => setLoginPromptOpen(false)}
            contextMessage="登录后即可与 AI 模型对话，体验智能问答能力"
          />
        </>
      ) : (
        <div className="flex h-[calc(100vh-96px)] bg-transparent">
          <Sidebar
            currentSessionId={currentSessionId}
            onNewChat={handleNewChat}
            onSelectSession={handleSelectSession}
            refreshTrigger={sidebarRefreshTrigger}
            selectedType={chatType}
            onSelectType={(type) => {
              setChatType(type);
              handleNewChat();
            }}
          />
          <ChatArea
            isMcpExecuting={isMcpExecuting}
            modelConversations={modelConversation}
            currentSessionId={currentSessionId}
            onChangeActiveAnswer={onChangeActiveAnswer}
            onSendMessage={handleSendMessage}
            onSelectProduct={handleSelectProduct}
            selectedModel={selectedModel}
            handleGenerateMessage={handleGenerateMessage}
            addModels={handleAddModels}
            closeModel={closeModel}
            generating={generating}
            chatType={chatType}
            onStop={handleStop}
          />
        </div>
      )}
    </Layout>
  );
}

export default Chat;
