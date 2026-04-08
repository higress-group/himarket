import { message as antdMessage } from 'antd';
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';

import { ChatArea } from '../components/chat/Area';
import { Sidebar } from '../components/chat/Sidebar';
import { Layout } from '../components/Layout';
import { LoginPrompt } from '../components/LoginPrompt';
import { WelcomeView } from '../components/WelcomeView';
import { useAuth } from '../hooks/useAuth';
import { useChatSession } from '../hooks/useChatSession';
import APIs, { type IProductDetail, type IAttachment } from '../lib/apis';

function Chat() {
  const location = useLocation();
  const { isLoggedIn } = useAuth();
  const { t: tLoginPrompt } = useTranslation('loginPrompt');
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [selectedModel, setSelectedModel] = useState<IProductDetail>();
  const [chatType, setChatType] = useState<'TEXT' | 'Image'>('TEXT');

  const {
    addModels,
    closeModel,
    currentSessionId,
    generating,
    handleNewChat,
    handleSelectSession,
    handleStop,
    isMcpExecuting,
    modelConversation,
    onChangeActiveAnswer,
    regenerateMessage,
    sendMessage,
    sidebarRefreshTrigger,
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
            ['modelFilter.category']: chatType,
            page: 0,
            size: 1,
            type: 'MODEL_API',
          });
          if (response.code === 'SUCCESS' && response.data?.content?.length > 0) {
            setSelectedModel(response.data.content[0]);
          } else {
            setSelectedModel(undefined);
          }
        } catch (error) {
          console.error('Failed to load default model:', error);
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
      antdMessage.error('请先选择一个模型');
      return;
    }
    await sendMessage(content, mcps, enableWebSearch, modelMap, selectedModel, attachments);
  };

  const handleGenerateMessage = async (params: {
    modelId: string;
    conversationId: string;
    questionId: string;
    content: string;
    mcps: IProductDetail[];
    enableWebSearch: boolean;
    modelMap: Map<string, IProductDetail>;
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
            contextMessage={tLoginPrompt('contextChatModel')}
            onClose={() => setLoginPromptOpen(false)}
            open={loginPromptOpen}
          />
        </>
      ) : (
        <div className="flex h-[calc(100vh-96px)] bg-transparent">
          <Sidebar
            currentSessionId={currentSessionId}
            onNewChat={handleNewChat}
            onSelectSession={handleSelectSession}
            onSelectType={(type) => {
              setChatType(type);
              handleNewChat();
            }}
            refreshTrigger={sidebarRefreshTrigger}
            selectedType={chatType}
          />
          <ChatArea
            addModels={handleAddModels}
            chatType={chatType}
            closeModel={closeModel}
            currentSessionId={currentSessionId}
            generating={generating}
            handleGenerateMessage={handleGenerateMessage}
            isMcpExecuting={isMcpExecuting}
            modelConversations={modelConversation}
            onChangeActiveAnswer={onChangeActiveAnswer}
            onSelectProduct={handleSelectProduct}
            onSendMessage={handleSendMessage}
            onStop={handleStop}
            selectedModel={selectedModel}
          />
        </div>
      )}
    </Layout>
  );
}

export default Chat;
