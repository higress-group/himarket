import { useState } from "react";
import { Form, Input, Button, Tag, Radio, Select, Switch, Table, Space, Image, message, Modal } from "antd";
import type { UploadFile } from "antd";
import {
  InfoCircleOutlined, SettingOutlined, FileTextOutlined,
  CameraOutlined, PlusOutlined, CheckCircleFilled,
  CodeOutlined, GlobalOutlined, ApiOutlined, DeleteOutlined, EditOutlined,
  ArrowLeftOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import APIs from "../lib/apis";

interface ExtraParam {
  key: string;
  name: string;
  position: string;
  required: boolean;
  description: string;
  example: string;
}

const STEPS = [
  { key: 0, label: "基础信息", icon: <InfoCircleOutlined />, desc: "名称、仓库、标签" },
  { key: 1, label: "MCP 配置", icon: <SettingOutlined />, desc: "协议与连接方式" },
  { key: 2, label: "服务介绍", icon: <FileTextOutlined />, desc: "Markdown 文档" },
];

function McpCreatePage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [currentStep, setCurrentStep] = useState(0);
  const [iconMode, setIconMode] = useState<"URL" | "BASE64">("URL");
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImage, setPreviewImage] = useState("");
  const [tagInput, setTagInput] = useState("");
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set());
  const [extraParams, setExtraParams] = useState<ExtraParam[]>([]);
  const [paramModalVisible, setParamModalVisible] = useState(false);
  const [paramForm] = Form.useForm();
  const [editingParamKey, setEditingParamKey] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const protocolType: string = Form.useWatch("protocolType", form) || "sse";
  const watchedTags: string[] = Form.useWatch("tags", form) || [];

  const getBase64 = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (error) => reject(error);
    });

  const handleIconModeChange = (mode: "URL" | "BASE64") => {
    setIconMode(mode);
    if (mode === "URL") { form.setFieldsValue({ icon: undefined }); setFileList([]); }
    else { form.setFieldsValue({ iconUrl: undefined }); }
  };

  const handleAddTag = () => {
    const val = tagInput.trim();
    if (!val) return;
    const tags: string[] = form.getFieldValue("tags") || [];
    if (tags.includes(val)) { message.warning("标签已存在"); return; }
    form.setFieldsValue({ tags: [...tags, val] });
    setTagInput("");
  };

  const handleRemoveTag = (tag: string) => {
    const tags: string[] = form.getFieldValue("tags") || [];
    form.setFieldsValue({ tags: tags.filter((t) => t !== tag) });
  };

  const stepFields: string[][] = [
    ["mcpServerName", "mcpDisplayName", "repoUrl"],
    ["mcpConfigJson"],
    [],
  ];

  const navigateTo = async (target: number) => {
    if (target > currentStep) {
      try {
        await form.validateFields(stepFields[currentStep]);
        setCompletedSteps((prev) => new Set(prev).add(currentStep));
      } catch { return; }
    }
    setCurrentStep(target);
  };

  const handleNext = async () => {
    try {
      await form.validateFields(stepFields[currentStep]);
      setCompletedSteps((prev) => new Set(prev).add(currentStep));
      setCurrentStep(currentStep + 1);
    } catch { /* validation failed */ }
  };

  const handleSubmit = async () => {
    try {
      await form.validateFields(stepFields[currentStep]);
      const values = form.getFieldsValue(true);

      let iconJson: string | undefined;
      if (iconMode === "URL" && values.iconUrl) {
        iconJson = JSON.stringify({ type: "URL", value: values.iconUrl });
      } else if (iconMode === "BASE64" && values.icon) {
        iconJson = JSON.stringify({ type: "BASE64", value: values.icon });
      }

      setSubmitting(true);
      const res = await APIs.registerMcp({
        mcpName: values.mcpServerName,
        displayName: values.mcpDisplayName,
        description: values.description,
        repoUrl: values.repoUrl,
        tags: values.tags?.length ? JSON.stringify(values.tags) : undefined,
        icon: iconJson,
        protocolType: values.protocolType || "sse",
        connectionConfig: values.mcpConfigJson,
        extraParams: extraParams.length ? JSON.stringify(extraParams) : undefined,
        serviceIntro: values.serviceIntro,
        sandboxRequired: values.sandboxRequired || false,
        origin: "USER",
      });

      if (res.code === "SUCCESS") {
        message.success("MCP 提交成功，等待管理员审核发布");
        navigate("/mcp");
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(error?.message || "提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  // ==================== Step 1: 基础信息 ====================
  const renderBasicInfo = () => (
    <>
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="mcpServerName"
          label={<span>MCP 英文名称 <span className="text-xs text-gray-400 font-normal ml-1">唯一标识</span></span>}
          rules={[
            { required: true, message: "请输入 MCP 英文名称" },
            { pattern: /^[a-z][a-z0-9-]*$/, message: "小写字母开头，仅含小写字母、数字、连字符" },
            { max: 63, message: "不超过 63 个字符" },
          ]}
        >
          <Input placeholder="weather-mcp-server" />
        </Form.Item>
        <Form.Item
          name="mcpDisplayName"
          label="MCP 中文名称"
          rules={[{ required: true, message: "请输入 MCP 中文名称" }, { max: 100, message: "不超过 100 个字符" }]}
        >
          <Input placeholder="天气查询服务" />
        </Form.Item>
      </div>

      <Form.Item name="description" label="描述">
        <Input.TextArea placeholder="简要描述 MCP Server 的功能和用途" rows={2} autoSize={{ minRows: 2, maxRows: 4 }} />
      </Form.Item>

      <Form.Item name="repoUrl" label="仓库地址"
        rules={[{ type: "url", message: "请输入合法的 URL" }]}
      >
        <Input placeholder="https://github.com/org/mcp-server" />
      </Form.Item>

      {/* 标签 */}
      <Form.Item label="自定义标签">
        <Form.Item name="tags" hidden><Input /></Form.Item>
        <div className="flex items-center gap-2 mb-2">
          <Input value={tagInput} onChange={(e) => setTagInput(e.target.value)}
            onPressEnter={(e) => { e.preventDefault(); handleAddTag(); }}
            placeholder="输入后按回车添加" size="small"
            suffix={<PlusOutlined className="text-gray-400 hover:text-colorPrimary cursor-pointer" onClick={handleAddTag} />}
          />
        </div>
        <div className="flex flex-wrap gap-1.5 min-h-[24px]">
          {watchedTags.map((tag: string) => (
            <Tag key={tag} closable onClose={() => handleRemoveTag(tag)} color="blue" className="m-0">{tag}</Tag>
          ))}
          {!watchedTags.length && <span className="text-xs text-gray-300">暂无标签</span>}
        </div>
      </Form.Item>

      {/* Icon */}
      <Form.Item label="Icon">
        <div className="flex items-start gap-4">
          {iconMode === "BASE64" ? (
            <div
              className="w-16 h-16 rounded-xl border-2 border-dashed border-gray-200 hover:border-colorPrimary/50 flex items-center justify-center cursor-pointer transition-all relative flex-shrink-0 group"
              onClick={() => {
                const input = document.createElement("input");
                input.type = "file"; input.accept = "image/*";
                input.onchange = (e) => {
                  const file = (e.target as HTMLInputElement).files?.[0];
                  if (!file) return;
                  if (file.size > 16 * 1024) { message.error("图片不能超过 16KB"); return; }
                  setFileList([{ uid: Date.now().toString(), name: file.name, status: "done", url: URL.createObjectURL(file) }]);
                  getBase64(file).then((b64) => form.setFieldsValue({ icon: b64 }));
                };
                input.click();
              }}
            >
              {fileList.length > 0 ? (
                <>
                  <img src={fileList[0].url} alt="icon" className="w-full h-full object-cover rounded-[10px]" />
                  <div className="absolute -top-1.5 -right-1.5 w-4 h-4 bg-red-500 rounded-full flex items-center justify-center text-white text-[10px] cursor-pointer opacity-0 group-hover:opacity-100 transition-opacity"
                    onClick={(e) => { e.stopPropagation(); setFileList([]); form.setFieldsValue({ icon: null }); }}>×</div>
                </>
              ) : (
                <div className="flex flex-col items-center text-gray-300 group-hover:text-colorPrimary transition-colors">
                  <CameraOutlined className="text-lg mb-0.5" /><span className="text-[10px]">上传</span>
                </div>
              )}
            </div>
          ) : (
            <Form.Item name="iconUrl" noStyle rules={[{ type: "url", message: "请输入有效的图片链接" }]}>
              <Input placeholder="图片链接地址" className="flex-1" />
            </Form.Item>
          )}
          <Radio.Group value={iconMode} onChange={(e) => handleIconModeChange(e.target.value)} size="small" optionType="button" buttonStyle="solid" className="flex-shrink-0">
            <Radio.Button value="URL">链接</Radio.Button>
            <Radio.Button value="BASE64">上传</Radio.Button>
          </Radio.Group>
        </div>
      </Form.Item>
      {previewImage && (
        <Image wrapperStyle={{ display: "none" }}
          preview={{ visible: previewOpen, onVisibleChange: setPreviewOpen, afterOpenChange: (v) => { if (!v) setPreviewImage(""); } }}
          src={previewImage} />
      )}
    </>
  );

  // ==================== Step 2: MCP 配置 ====================
  const renderMcpConfig = () => {
    const protocols = [
      { key: "stdio", label: "Stdio", icon: <CodeOutlined /> },
      { key: "sse", label: "SSE", icon: <ApiOutlined /> },
      { key: "http", label: "Streamable HTTP", icon: <GlobalOutlined /> },
    ];
    const isStdio = protocolType === "stdio";
    const exampleJson = isStdio
      ? '{\n  "mcpServers": {\n    "your-server": {\n      "command": "npx",\n      "args": ["-y", "@mcp/your-server"]\n    }\n  }\n}'
      : protocolType === "sse"
        ? '{\n  "mcpServers": {\n    "your-server": {\n      "type": "sse",\n      "url": "https://mcp.example.com/sse"\n    }\n  }\n}'
        : '{\n  "mcpServers": {\n    "your-server": {\n      "url": "https://mcp.example.com/mcp"\n    }\n  }\n}';

    return (
      <>
        <Form.Item name="protocolType" hidden initialValue="sse" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item label="协议类型">
          <div className="grid grid-cols-3 gap-3">
            {protocols.map((p) => {
              const selected = protocolType === p.key;
              return (
                <div key={p.key}
                  onClick={() => { form.setFieldsValue({ protocolType: p.key, ...(p.key === "stdio" ? { sandboxRequired: true } : {}) }); setExtraParams([]); }}
                  className={`relative flex items-center gap-2.5 rounded-xl border px-4 py-3 cursor-pointer transition-all duration-200 ${
                    selected ? "border-colorPrimary bg-colorPrimary/5 ring-1 ring-colorPrimary/20" : "border-gray-200 hover:border-gray-300 hover:bg-gray-50/50"
                  }`}
                >
                  {selected && <CheckCircleFilled className="absolute top-2 right-2 text-colorPrimary text-xs" />}
                  <span className={`text-base ${selected ? "text-colorPrimary" : "text-gray-400"}`}>{p.icon}</span>
                  <span className={`text-sm font-medium ${selected ? "text-colorPrimary" : "text-gray-600"}`}>{p.label}</span>
                </div>
              );
            })}
          </div>
        </Form.Item>

        <Form.Item name="sandboxRequired" label="沙箱托管" valuePropName="checked" initialValue={true}>
          <Switch checkedChildren="需要" unCheckedChildren="不需要" disabled={protocolType === "stdio"} />
        </Form.Item>

        <Form.Item name="mcpConfigJson" label="MCP 连接配置"
          rules={[
            { required: true, message: "请输入 MCP 配置" },
            { validator: (_, value) => {
                if (!value) return Promise.resolve();
                try { JSON.parse(value); return Promise.resolve(); }
                catch { return Promise.reject("请输入合法的 JSON 格式"); }
              },
            },
          ]}
        >
          <Input.TextArea placeholder={exampleJson} autoSize={{ minRows: 8, maxRows: 14 }} className="font-mono text-xs" />
        </Form.Item>

        <div className="flex items-center gap-3 -mt-3 mb-4">
          <Button size="small" type="primary" ghost icon={<CodeOutlined />}
            onClick={() => {
              const raw = form.getFieldValue("mcpConfigJson");
              if (!raw) { message.warning("请先粘贴 JSON 配置"); return; }
              try {
                const parsed = JSON.parse(raw);
                const servers = parsed.mcpServers || parsed;
                const serverKey = Object.keys(servers)[0];
                if (!serverKey) { message.error("未找到有效的 MCP Server 配置"); return; }
                const cfg = servers[serverKey];
                if (cfg.command) form.setFieldsValue({ protocolType: "stdio", sandboxRequired: true });
                else if (cfg.type === "sse") form.setFieldsValue({ protocolType: "sse" });
                else form.setFieldsValue({ protocolType: "http" });
                const params: ExtraParam[] = [];
                if (cfg.env && typeof cfg.env === "object") {
                  Object.entries(cfg.env).forEach(([k, v]) => {
                    params.push({ key: `p_${Date.now()}_${k}`, name: k, position: "env", required: true, description: "", example: String(v) });
                  });
                }
                if (cfg.headers && typeof cfg.headers === "object") {
                  Object.entries(cfg.headers).forEach(([k, v]) => {
                    params.push({ key: `p_${Date.now()}_${k}`, name: k, position: "header", required: true, description: "", example: String(v) });
                  });
                }
                setExtraParams(params);
                message.success(`已解析：${serverKey}，识别到 ${params.length} 个参数`);
              } catch { message.error("JSON 解析失败，请检查格式"); }
            }}
          >解析 JSON</Button>
          <span className="text-xs text-gray-400">粘贴 JSON 后点击可自动解析协议类型及参数</span>
        </div>

        {/* 额外参数 */}
        <div className="mt-1">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-gray-700">{isStdio ? "环境变量配置" : "请求参数配置"}</span>
            <Space size={8}>
              <Button size="small" type="dashed" icon={<PlusOutlined />}
                onClick={() => { setEditingParamKey(null); paramForm.resetFields(); setParamModalVisible(true); }}>
                添加{isStdio ? "变量" : "参数"}
              </Button>
              {extraParams.length > 0 && (
                <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => setExtraParams([])}>清除所有</Button>
              )}
            </Space>
          </div>
          {extraParams.length > 0 ? (
            <Table dataSource={extraParams} rowKey="key" size="small" pagination={false}
              className="border border-gray-100 rounded-lg overflow-hidden"
              columns={[
                { title: "参数名", dataIndex: "name", width: 120, render: (v: string) => <span className="font-mono text-xs">{v}</span> },
                { title: "位置", dataIndex: "position", width: 80, render: (v: string) => <Tag className="m-0 border-0 bg-gray-100 text-gray-600 text-xs">{v}</Tag> },
                { title: "必填", dataIndex: "required", width: 50, align: "center" as const,
                  render: (v: boolean) => v ? <Tag color="red" className="m-0 border-0 text-xs">是</Tag> : <span className="text-xs text-gray-400">否</span> },
                { title: "说明", dataIndex: "description", ellipsis: true, render: (v: string) => <span className="text-xs text-gray-500">{v || "-"}</span> },
                { title: "", width: 60, align: "center" as const,
                  render: (_: any, record: ExtraParam) => (
                    <Space size={4}>
                      <Button type="text" size="small" icon={<EditOutlined />} className="text-gray-400 hover:text-colorPrimary"
                        onClick={() => { setEditingParamKey(record.key); paramForm.setFieldsValue(record); setParamModalVisible(true); }} />
                      <Button type="text" size="small" icon={<DeleteOutlined />} className="text-gray-400 hover:text-red-500"
                        onClick={() => setExtraParams((prev) => prev.filter((p) => p.key !== record.key))} />
                    </Space>
                  ),
                },
              ]}
            />
          ) : (
            <div className="border border-dashed border-gray-200 rounded-xl py-8 text-center text-xs text-gray-400">
              暂无{isStdio ? "环境变量" : "请求参数"}，点击上方按钮添加
            </div>
          )}
        </div>
      </>
    );
  };

  // ==================== Step 3: 服务介绍 ====================
  const renderServiceIntro = () => (
    <>
      <div className="flex items-center justify-between mb-3">
        <div>
          <div className="text-sm font-medium text-gray-700">服务详情文档</div>
          <div className="text-xs text-gray-400 mt-0.5">使用 Markdown 语法编写，发布后将渲染为富文本</div>
        </div>
        <Tag color="blue" className="m-0 border-0">Markdown</Tag>
      </div>
      <Form.Item name="serviceIntro" className="mb-0">
        <Input.TextArea
          placeholder={"# 服务介绍\n\n简要描述你的 MCP Server...\n\n## 功能特性\n\n- 特性一\n- 特性二"}
          autoSize={{ minRows: 16, maxRows: 22 }}
          className="font-mono text-xs"
        />
      </Form.Item>
    </>
  );

  const stepContent = [renderBasicInfo, renderMcpConfig, renderServiceIntro];

  return (
    <Layout>
      <div className="h-[calc(100vh-96px)] overflow-auto">
        {/* 顶部面包屑 */}
        <div className="px-8 pt-4 pb-2">
          <button onClick={() => navigate("/mcp")}
            className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-colorPrimary transition-colors">
            <ArrowLeftOutlined className="text-xs" />返回 MCP 广场
          </button>
        </div>

        {/* 居中卡片容器 */}
        <div className="max-w-[960px] mx-auto px-8 pb-8">
          {/* 页面标题 */}
          <div className="mb-6">
            <h1 className="text-xl font-semibold text-gray-900">创建 MCP Server</h1>
            <p className="text-sm text-gray-400 mt-1">填写信息后提交，管理员审核通过后将发布到广场</p>
          </div>

          <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 shadow-sm overflow-hidden">
            <div className="flex" style={{ minHeight: 560 }}>
              {/* 左侧步骤导航 */}
              <div className="w-56 bg-gray-50/60 border-r border-gray-100/80 p-6 flex flex-col flex-shrink-0">
                <nav className="flex-1 space-y-0">
                  {STEPS.map((item, idx) => {
                    const isActive = currentStep === item.key;
                    const isDone = completedSteps.has(item.key);
                    const isLast = idx === STEPS.length - 1;
                    return (
                      <div key={item.key} className="flex gap-3 cursor-pointer" onClick={() => navigateTo(item.key)}>
                        <div className="flex flex-col items-center flex-shrink-0">
                          <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-medium transition-all duration-200 ${
                            isActive ? "bg-colorPrimary text-white shadow-sm shadow-colorPrimary/30"
                              : isDone ? "bg-green-500 text-white" : "bg-gray-200 text-gray-500"
                          }`}>
                            {isDone && !isActive ? <CheckCircleFilled className="text-xs" /> : idx + 1}
                          </div>
                          {!isLast && (
                            <div className={`w-0.5 flex-1 my-1.5 min-h-[32px] rounded-full transition-colors duration-300 ${isDone ? "bg-green-300" : "bg-gray-200"}`} />
                          )}
                        </div>
                        <div className="pt-1 pb-5">
                          <div className={`text-sm leading-tight ${isActive ? "font-semibold text-gray-900" : isDone ? "font-medium text-gray-700" : "text-gray-400"}`}>
                            {item.label}
                          </div>
                          <div className="text-[11px] text-gray-400 mt-1">{item.desc}</div>
                        </div>
                      </div>
                    );
                  })}
                </nav>
              </div>

              {/* 右侧表单内容 */}
              <div className="flex-1 flex flex-col min-w-0">
                {/* 步骤标题栏 */}
                <div className="flex items-center gap-2.5 px-7 py-4 border-b border-gray-100/80">
                  <span className="text-colorPrimary/60">{STEPS[currentStep].icon}</span>
                  <span className="text-sm font-medium text-gray-800">{STEPS[currentStep].label}</span>
                  <span className="text-xs text-gray-400">— {STEPS[currentStep].desc}</span>
                </div>

                {/* 表单区域 */}
                <div className="flex-1 overflow-auto px-7 py-6">
                  <Form form={form} layout="vertical" requiredMark="optional">
                    {stepContent[currentStep]()}
                  </Form>
                </div>

                {/* 底部操作栏 */}
                <div className="flex items-center justify-between px-7 py-4 border-t border-gray-100/80 bg-gray-50/30">
                  <div>
                    {currentStep > 0 && (
                      <Button onClick={() => setCurrentStep(currentStep - 1)}>上一步</Button>
                    )}
                  </div>
                  <Space>
                    <Button onClick={() => navigate("/mcp")}>取消</Button>
                    {currentStep < 2 ? (
                      <Button type="primary" onClick={handleNext}>下一步</Button>
                    ) : (
                      <Button type="primary" onClick={handleSubmit} loading={submitting}>提交审核</Button>
                    )}
                  </Space>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 添加/编辑参数弹窗 */}
      <Modal
        title={editingParamKey ? "编辑参数" : "添加参数"}
        open={paramModalVisible}
        onCancel={() => { setParamModalVisible(false); paramForm.resetFields(); setEditingParamKey(null); }}
        onOk={() => {
          paramForm.validateFields().then((values) => {
            if (editingParamKey) {
              setExtraParams((prev) => prev.map((p) => p.key === editingParamKey ? { ...values, key: editingParamKey } : p));
            } else {
              setExtraParams((prev) => [...prev, { ...values, key: `param_${Date.now()}` }]);
            }
            setParamModalVisible(false); paramForm.resetFields(); setEditingParamKey(null);
          });
        }}
        okText="确定" cancelText="取消" width={480} destroyOnClose
      >
        <Form form={paramForm} layout="vertical" className="mt-4">
          <Form.Item name="name" label="参数名" rules={[{ required: true, message: "请输入参数名" }]}>
            <Input placeholder={protocolType === "stdio" ? "例如: API_KEY" : "例如: Authorization"} />
          </Form.Item>
          <Form.Item name="position" label="参数位置" initialValue={protocolType === "stdio" ? "env" : "header"} rules={[{ required: true }]}>
            <Select>
              {protocolType === "stdio" ? (
                <Select.Option value="env">环境变量 (env)</Select.Option>
              ) : (
                <><Select.Option value="header">请求头 (header)</Select.Option><Select.Option value="query">查询参数 (query)</Select.Option></>
              )}
            </Select>
          </Form.Item>
          <Form.Item name="required" label="是否必填" valuePropName="checked" initialValue={false}>
            <Switch checkedChildren="必填" unCheckedChildren="可选" />
          </Form.Item>
          <Form.Item name="description" label="参数说明">
            <Input.TextArea placeholder="描述该参数的用途" rows={2} autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
          <Form.Item name="example" label="参数示例">
            <Input placeholder={protocolType === "stdio" ? "例如: sk-xxxxxxxxxxxx" : "例如: Bearer sk-xxx"} />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  );
}

export default McpCreatePage;
