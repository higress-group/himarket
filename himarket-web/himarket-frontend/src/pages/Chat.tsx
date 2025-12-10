import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import { message as antdMessage } from "antd";
import { Layout } from "../components/Layout";
import { Sidebar } from "../components/chat/Sidebar";
import { generateConversationId, generateQuestionId } from "../lib/uuid";
import { handleSSEStream, } from "../lib/sse";
import APIs, { type IProductConversations, type IProductDetail } from "../lib/apis";
import type { IModelConversation } from "../types";
import { ChatArea } from "../components/chat/Area";


function Chat() {
  const location = useLocation();
  const [currentSessionId, setCurrentSessionId] = useState<string>();
  const [selectedModel, setSelectedModel] = useState<IProductDetail>();
  const [useStream] = useState(true); // 默认使用流式响应
  const [sidebarRefreshTrigger, setSidebarRefreshTrigger] = useState(0); // 用于触发 Sidebar 刷新
  // 多模型对比的初始化数据（用于从历史会话加载）

  const [modelConversation, setModelConversation] = useState<IModelConversation[]>([]);
  const [isMcpExecuting, setIsMcpExecuting] = useState(false);

  const [generating, setGenerating] = useState(false);

  // 从 location.state 接收选中的产品，或者加载默认第一个模型
  useEffect(() => {
    const state = location.state as { selectedProduct?: IProductDetail } | null;
    if (state?.selectedProduct) {
      setSelectedModel(state.selectedProduct);
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
            setSelectedModel(response.data.content[0]);
          }
        } catch (error) {
          console.error("Failed to load default model:", error);
        }
      };
      loadDefaultModel();
    }
  }, [location]);

  const handleSendMessage = async (content: string, mcps: IProductDetail[], enableWebSearch: boolean, modelMap: Map<string, IProductDetail>) => {
    if (!selectedModel) {
      antdMessage.error("请先选择一个模型");
      return;
    }
    try {
      setGenerating(true);
      // 如果没有会话，先创建会话
      let sessionId = currentSessionId;
      if (!sessionId) {
        const sessionResponse = await APIs.createSession({
          talkType: "MODEL",
          name: content.length > 20 ? content.substring(0, 20) + "..." : content, // 只在超过20个字符时添加省略号
          products: modelConversation.length ? modelConversation.map(v => v.id) : [selectedModel.productId],
        });

        if (sessionResponse.code === "SUCCESS") {
          sessionId = sessionResponse.data.sessionId;
          setCurrentSessionId(sessionId);
          // 触发 Sidebar 刷新以显示新会话
          setSidebarRefreshTrigger(prev => prev + 1);
        } else {
          setGenerating(false);
          throw new Error("创建会话失败");
        }
      }

      // 每次新问答都生成新的 conversationId 和 questionId
      const conversationId = generateConversationId();
      const questionId = generateQuestionId();


      // 发送消息（sessionId 已确保不为 null）
      if (!sessionId) {
        throw new Error("会话ID不存在");
      }

      const modelIds = modelConversation.length ? modelConversation.map(model => model.id) : [selectedModel.productId];
      // const modelInsts = modelIds.map(id => modelMap.get(id));
      const requests = modelIds.map(async (modelId) => {
        const isSupport = modelMap.get(modelId)?.feature?.modelFeature?.webSearch || false;
        const messagePayload = {
          productId: modelId,
          sessionId,
          conversationId,
          questionId,
          question: content,
          stream: useStream,
          needMemory: true,
          mcpProducts: mcps.map(mcp => mcp.productId),
          enableWebSearch: enableWebSearch ? isSupport : false,
        };

        let fullContent = '';

        setModelConversation(prev => {
          if (prev.length === 0) {
            return [
              {
                id: selectedModel.productId,
                sessionId: currentSessionId!,
                name: "-",
                conversations: [
                  {
                    id: conversationId,
                    loading: true,
                    questions: [
                      {
                        id: questionId,
                        content,
                        createdAt: new Date().toDateString(),
                        activeAnswerIndex: 0,
                        answers: [
                          {
                            errorMsg: "",
                            content: fullContent,
                            firstTokenTime: 0,
                            totalTime: 0,
                            inputTokens: 0,
                            outputTokens: 0,
                          }
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
          }
          return prev.map(model => {
            if (model.id !== modelId) return model;
            return {
              ...model,
              conversations: [
                ...model.conversations,
                {
                  id: conversationId,
                  loading: true,
                  questions: [
                    {
                      id: questionId,
                      content,
                      createdAt: new Date().toDateString(),
                      activeAnswerIndex: 0,
                      answers: [],
                    }
                  ]
                }
              ]
            }
          })
        })


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
            onToolCall: (toolCall) => {
              setIsMcpExecuting(true);
              setModelConversation((prev) => {
                return prev.map(model => {
                  if (model.id !== modelId) return model;
                  return {
                    ...model,
                    conversations: model.conversations.map(con => {
                      if (con.id !== conversationId) return con;
                      return {
                        ...con,
                        questions: con.questions.map(question => {
                          if (question.id === questionId) {
                            return {
                              ...question,
                              mcpToolCalls: [...(question.mcpToolCalls || []), toolCall]
                            };
                          }
                          return question;
                        })
                      };
                    })
                  };
                });
              });
            },
            onToolResponse: (toolResponse) => {
              setIsMcpExecuting(false);
              setModelConversation((prev) => {
                return prev.map(model => {
                  if (model.id !== modelId) return model;
                  return {
                    ...model,
                    conversations: model.conversations.map(con => {
                      if (con.id !== conversationId) return con;
                      return {
                        ...con,
                        questions: con.questions.map(question => {
                          if (question.id === questionId) {
                            return {
                              ...question,
                              mcpToolResponses: [...(question.mcpToolResponses || []), toolResponse]
                            };
                          }
                          return question;
                        })
                      };
                    })
                  };
                });
              });
            },
            onChunk: (chunk) => {
              fullContent += chunk;
              setModelConversation((prev) => {
                return prev.map(model => {
                  if (model.id !== modelId) return model;
                  return {
                    ...model,
                    conversations: model.conversations.map(con => {
                      return {
                        ...con,
                        loading: false,
                        questions: con.questions.map(question => {
                          if (question.id === questionId) {
                            return {
                              ...question,
                              answers: [
                                {
                                  errorMsg: "",
                                  content: fullContent,
                                  firstTokenTime: 0,
                                  totalTime: 0,
                                  inputTokens: 0,
                                  outputTokens: 0,
                                }
                              ]
                            }
                          } else {
                            return question
                          }
                        })
                      }
                    })
                  }
                })
              })
            },
            onComplete: (_content, _chatId, usage) => {
              setIsMcpExecuting(false);
              setModelConversation((prev) => {
                return prev.map(model => {
                  if (model.id !== modelId) return model;
                  return {
                    ...model,
                    conversations: model.conversations.map(con => {
                      return {
                        ...con,
                        questions: con.questions.map(question => {
                          if (question.id === questionId) {
                            return {
                              ...question,
                              answers: [
                                {
                                  errorMsg: question.answers?.[0]?.errorMsg,
                                  content: fullContent,
                                  firstTokenTime: usage?.first_byte_timeout || 0,
                                  totalTime: usage?.elapsed_time || 0,
                                  inputTokens: usage?.prompt_tokens || 0,
                                  outputTokens: usage?.completion_tokens || 0,
                                }
                              ]
                            }
                          } else {
                            return question
                          }
                        })
                      }
                    })
                  }
                })
              })
            },
            onError: (errorMsg) => {
              setIsMcpExecuting(false);
              setModelConversation((prev) => {
                return prev.map(model => {
                  if (model.id !== modelId) return model;
                  return {
                    ...model,
                    conversations: model.conversations.map(con => {
                      return {
                        ...con,
                        loading: false,
                        questions: con.questions.map(question => {
                          if (question.id === questionId) {
                            return {
                              ...question,
                              answers: [
                                {
                                  errorMsg,
                                  content: fullContent,
                                  firstTokenTime: 0,
                                  totalTime: 0,
                                  inputTokens: 0,
                                  outputTokens: 0,
                                }
                              ]
                            }
                          } else {
                            return question
                          }
                        })
                      }
                    })
                  }
                })
              })
            },
          }
        );
      });

      // 等待所有请求完成
      setGenerating(true);
      await Promise.allSettled(requests);
      setGenerating(false);

    } catch (error) {
      setModelConversation((prev) => {
        return prev.map(model => {
          return {
            ...model,
            conversations: model.conversations.map(con => {
              return {
                ...con,
                loading: false,
                questions: con.questions.map((question, idx) => {
                  if (idx === con.questions.length - 1) {
                    return {
                      ...question,
                      answers: [
                        {
                          errorMsg: "网络错误，请重试",
                          content: "",
                          firstTokenTime: 0,
                          totalTime: 0,
                          inputTokens: 0,
                          outputTokens: 0,
                        }
                      ]
                    }
                  } else {
                    return question
                  }
                })
              }
            })
          }
        })
      });
      setGenerating(false);
      console.error("Failed to send message:", error);
    }
  };

  // 重新生成答案
  const handleGenerateMessage = async ({
    modelId, conversationId, questionId, content,
    mcps, enableWebSearch, modelMap
  }: {
    modelId: string, conversationId: string, questionId: string, content: string,
    mcps: IProductDetail[], enableWebSearch: boolean, modelMap: Map<string, IProductDetail>
  }) => {
    setGenerating(true);
    const isSupportWebSearch = modelMap.get(modelId)?.feature?.modelFeature?.webSearch || false;
    try {
      const messagePayload = {
        productId: modelId, sessionId: currentSessionId,
        conversationId, questionId,
        question: content, stream: true,
        needMemory: true,
        mcpProducts: mcps.map(mcp => mcp.productId),
        enableWebSearch: enableWebSearch ? isSupportWebSearch : false
      };
      let fullContent = '';
      let lastIdx = -1;

      // 加载 loading
      setModelConversation(prev => {
        return prev.map(model => {
          if (model.id !== modelId) return model;
          return {
            ...model,
            conversations: model.conversations.map(con => {
              return {
                ...con,
                loading: con.id === conversationId,
                questions: con.questions.map(question => (
                  {
                    ...question,
                    isNewQuestion: question.id === questionId ? true : question.isNewQuestion
                  }
                ))
              };
            })
          }
        })
      })

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
        }, {
        onToolCall: (toolCall) => {
          setIsMcpExecuting(true);
          setModelConversation((prev) => {
            return prev.map(model => {
              if (model.id !== modelId) return model;
              return {
                ...model,
                conversations: model.conversations.map(con => {
                  if (con.id !== conversationId) return con;
                  return {
                    ...con,
                    questions: con.questions.map(question => {
                      if (question.id === questionId) {
                        return {
                          ...question,
                          mcpToolCalls: [...(question.mcpToolCalls || []), toolCall]
                        };
                      }
                      return question;
                    })
                  };
                })
              };
            });
          });
        },
        onToolResponse: (toolResponse) => {
          setIsMcpExecuting(false);
          setModelConversation((prev) => {
            return prev.map(model => {
              if (model.id !== modelId) return model;
              return {
                ...model,
                conversations: model.conversations.map(con => {
                  if (con.id !== conversationId) return con;
                  return {
                    ...con,
                    questions: con.questions.map(question => {
                      if (question.id === questionId) {
                        return {
                          ...question,
                          mcpToolResponses: [...(question.mcpToolResponses || []), toolResponse]
                        };
                      }
                      return question;
                    })
                  };
                })
              };
            });
          });
        },
        onChunk: (chunk) => {
          fullContent += chunk;
          setModelConversation((prev) => {
            return prev.map(model => {
              if (model.id !== modelId) return model;
              return {
                ...model,
                conversations: model.conversations.map(con => {
                  if (con.id !== conversationId) return con;
                  return {
                    ...con,
                    loading: false,
                    questions: con.questions.map(question => {
                      if (question.id !== questionId) return question;
                      const ans = lastIdx !== -1 ? question.answers.map((answer, idx) => {
                        if (idx !== question.answers.length - 1) return answer;
                        return {
                          ...answer,
                          content: fullContent,
                        }
                      }) : [
                        ...question.answers,
                        {
                          errorMsg: "",
                          content: fullContent,
                          firstTokenTime: 0,
                          totalTime: 0,
                          inputTokens: 0,
                          outputTokens: 0,
                        }
                      ]
                      if (lastIdx === -1) {
                        lastIdx = question.answers.length + 1;
                      }
                      return {
                        ...question,
                        activeAnswerIndex: ans.length - 1,
                        answers: ans
                      }
                    })
                  }
                })
              }
            })
          })
        },
        onComplete: (_content, _chatId, usage) => {
          setIsMcpExecuting(false);
          setModelConversation((prev) => {
            return prev.map(model => {
              if (model.id !== modelId) return model;
              return {
                ...model,
                conversations: model.conversations.map(con => {
                  if (con.id !== conversationId) return con;
                  return {
                    ...con,
                    loading: false,
                    questions: con.questions.map(question => {
                      if (question.id !== questionId) return question;
                      return {
                        ...question,
                        activeAnswerIndex: question.answers.length - 1,
                        answers: question.answers.map((answer, idx) => {
                          if (idx === question.answers.length - 1) {
                            return {
                              errorMsg: answer.errorMsg,
                              content: fullContent,
                              firstTokenTime: usage?.first_byte_timeout || 0,
                              totalTime: usage?.elapsed_time || 0,
                              inputTokens: usage?.prompt_tokens || 0,
                              outputTokens: usage?.completion_tokens || 0,
                            }
                          }
                          return answer;
                        })
                      }
                    })
                  }
                })
              }
            })
          });
          setGenerating(false);
        },
        onError: (errorMsg) => {
          setIsMcpExecuting(false);
          setModelConversation((prev) => {
            return prev.map(model => {
              return {
                ...model,
                conversations: model.conversations.map(con => {
                  if (con.id !== conversationId) return con;
                  return {
                    ...con,
                    loading: false,
                    questions: con.questions.map(question => {
                      if (question.id !== questionId) return question;
                      return {
                        ...question,
                        answers: [
                          ...question.answers,
                          {
                            errorMsg,
                            content: fullContent,
                            firstTokenTime: 0,
                            totalTime: 0,
                            inputTokens: 0,
                            outputTokens: 0,
                          }
                        ]
                      }
                    })
                  }
                })
              }
            })
          });
          setGenerating(false)
        }
      })
    } catch (error) {
      setGenerating(false);
      console.log(error)
    }

  };

  const handleNewChat = () => {
    setModelConversation([]);
    setCurrentSessionId(undefined);
  };

  const handleSelectProduct = (product: IProductDetail) => {
    // 当重新选择模型时，开启新会话
    setSelectedModel(product);
    // 清除当前会话状态
    setCurrentSessionId(undefined);
    setModelConversation([]);
  };

  const onChangeActiveAnswer = (modelId: string, conversationId: string, questionId: string, direction: 'prev' | 'next') => {
    setModelConversation(prev => prev.map(model => {
      if (model.id === modelId) {
        return {
          ...model,
          conversations: model.conversations.map(conversation => {
            if (conversation.id === conversationId) {
              return {
                ...conversation,
                questions: conversation.questions.map(question => {
                  if (question.id === questionId) {
                    let newIndex = question.activeAnswerIndex;
                    if (direction === 'prev' && newIndex > 0) {
                      newIndex = newIndex - 1;
                    } else if (direction === 'next' && newIndex < question.answers.length - 1) {
                      newIndex = newIndex + 1;
                    }
                    return {
                      ...question,
                      activeAnswerIndex: newIndex,
                    };
                  }
                  return question;
                }),
              };
            }
            return conversation;
          }),
        };
      }
      return model;
    }));
  }


  // 加载会话的历史聊天记录
  const handleSelectSession = async (sessionId: string) => {
    // 如果点击的是当前已选中的会话，不重复加载
    if (currentSessionId === sessionId) {
      return;
    }

    setGenerating(false);

    try {
      setCurrentSessionId(sessionId);
      // 不要立即清空消息，避免闪烁
      // setGenerating(true);
      const response = await APIs.getConversationsV2(sessionId);

      if (response.code === "SUCCESS" && response.data) {
        const models: IProductConversations[] = response.data;

        const m: IModelConversation[] = models.map(model => {
          return {
            id: model.productId,
            sessionId,
            name: "-", // TODO
            conversations: model.conversations.map(conversation => {
              return {
                id: conversation.conversationId,
                loading: false,
                questions: conversation.questions.map(question => {
                  return {
                    id: question.questionId,
                    content: question.content,
                    createdAt: question.createdAt,
                    activeAnswerIndex: question.answers.length - 1,
                    isNewQuestion: false,
                    answers: question.answers.map(answer => {
                      return {
                        errorMsg: "",
                        content: answer.content,
                        usage: answer.usage,
                        firstTokenTime: answer.usage?.first_byte_timeout || 0,
                        totalTime: answer.usage?.elapsed_time || 0,
                        inputTokens: answer.usage?.prompt_tokens || 0,
                        outputTokens: answer.usage?.completion_tokens || 0,
                      }
                    })
                  }
                })
              }
            })
          }
        })
        setModelConversation(m);
        // setGenerating(false)
        return;
      }
    } catch (error) {
      // setGenerating(false);
      console.error("Failed to load conversation:", error);
      antdMessage.error("加载聊天记录失败");
    }
  };

  const addModels = (modelIds: string[]) => {
    setCurrentSessionId(undefined);
    if (modelConversation.length === 0) {
      setModelConversation([
        {
          id: selectedModel?.productId || "",
          name: selectedModel?.name || "",
          conversations: [],
          sessionId: currentSessionId || "",
        },
        ...modelIds.map(id => {
          return {
            sessionId: currentSessionId || "",
            id,
            name: "",
            conversations: [],
          }
        }),
      ])
    } else {
      setModelConversation(prev => {
        return [
          ...prev.map(model => {
            return {
              sessionId: currentSessionId || "",
              id: model.id,
              name: "",
              conversations: [],
            }
          }),
          ...modelIds.map(id => {
            return {
              sessionId: currentSessionId || "",
              id,
              name: "",
              conversations: [],
            }
          }),
        ]
      })
    }
  }

  const closeModel = (modelId: string) => {
    setModelConversation(prev => {
      return prev.filter(model => model.id !== modelId);
    })
  }


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
          isMcpExecuting={isMcpExecuting}
          modelConversations={modelConversation}
          currentSessionId={currentSessionId}
          onChangeActiveAnswer={onChangeActiveAnswer}
          onSendMessage={handleSendMessage}
          onSelectProduct={handleSelectProduct}
          selectedModel={selectedModel}
          handleGenerateMessage={handleGenerateMessage}
          addModels={addModels}
          closeModel={closeModel}
          generating={generating}
        />
      </div>
    </Layout>
  );
}

export default Chat;
