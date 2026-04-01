import { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Alert, Button, Select, Tag, Tooltip } from "antd";
import { ArrowLeftOutlined, DownloadOutlined, CopyOutlined, CheckOutlined, UserOutlined, FileFilled, CodeOutlined, EyeOutlined, CloudUploadOutlined } from "@ant-design/icons";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";
import type { IProductDetail } from "../lib/apis";
import type { IProductIcon } from "../lib/apis/typing";
import type { IWorkerConfig } from "../lib/apis/typing";
import type { WorkerFileTreeNode, WorkerFileContent, WorkerVersion, WorkerCliInfo } from "../lib/apis/workerTemplateApi";
import APIs from "../lib/apis";
import { getWorkerFileTree, getWorkerFileContent, getWorkerVersions, getWorkerPackageUrl, getWorkerCliInfo } from "../lib/apis/workerTemplateApi";
import MarkdownRender from "../components/MarkdownRender";
import { parseSkillMd } from "../lib/skillMdUtils";
import SkillFileTree from "../components/skill/SkillFileTree";
import { copyToClipboard } from "../lib/utils";
import { DetailSkeleton } from "../components/loading";

function MdPreview({ content }: { content: string }) {
  const { frontmatter, body } = parseSkillMd(content);
  const fmEntries = Object.entries(frontmatter);
  return (
    <div className="markdown-body text-sm">
      {fmEntries.length > 0 && (
        <table className="mb-6 w-full text-[13px] border-collapse">
          <thead>
            <tr className="bg-[#f6f8fa]">
              {fmEntries.map(([k]) => (
                <th key={k} className="border border-[#d0d7de] px-3 py-1.5 text-left font-semibold text-[#1f2328]">{k}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              {fmEntries.map(([k, v]) => (
                <td key={k} className="border border-[#d0d7de] px-3 py-1.5 text-[#1f2328] align-top">{v}</td>
              ))}
            </tr>
          </tbody>
        </table>
      )}
      <MarkdownRender content={body} />
    </div>
  );
}

function inferLanguage(path: string): string {
  const fileName = path.split("/").pop()?.toLowerCase() ?? "";
  if (fileName === "dockerfile") return "dockerfile";
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  const map: Record<string, string> = {
    ts: "typescript", tsx: "typescript", js: "javascript", jsx: "javascript",
    py: "python", java: "java", go: "go", rs: "rust", cpp: "cpp", c: "c",
    sh: "bash", bash: "bash", yaml: "yaml", yml: "yaml", json: "json",
    toml: "ini", xml: "xml", html: "xml", css: "css", md: "markdown",
    sql: "sql", rb: "ruby", kt: "kotlin", swift: "swift", h: "c", hpp: "cpp",
    cfg: "ini", ini: "ini",
  };
  return map[ext] ?? "plaintext";
}

function getIconUrl(icon?: IProductIcon): string | null {
  if (!icon) return null;
  if (icon.type === "URL" && icon.value) return icon.value;
  if (icon.type === "BASE64" && icon.value) {
    return icon.value.startsWith("data:") ? icon.value : `data:image/png;base64,${icon.value}`;
  }
  return null;
}

function ProductIcon({ name, icon }: { name: string; icon?: IProductIcon }) {
  const iconUrl = getIconUrl(icon);

  if (iconUrl) {
    return (
      <img
        src={iconUrl}
        alt={name}
        className="w-16 h-16 rounded-xl flex-shrink-0 object-cover border border-gray-200"
        onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
      />
    );
  }

  return (
    <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gray-50 border border-gray-200">
      <UserOutlined className="text-3xl text-black" />
    </div>
  );
}

function WorkerDetail() {
  const { workerProductId } = useParams<{ workerProductId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<IProductDetail>();
    const [workerConfig, setWorkerConfig] = useState<IWorkerConfig>();

  const [fileTree, setFileTree] = useState<WorkerFileTreeNode[]>([]);
  const [selectedFilePath, setSelectedFilePath] = useState<string | undefined>();
  const [fileContent, setFileContent] = useState<WorkerFileContent | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [treeWidth, setTreeWidth] = useState(224);
  const isDragging = useRef(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'file'>('overview');
  const [overviewContent, setOverviewContent] = useState<string | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [versions, setVersions] = useState<WorkerVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<string | undefined>();

  const handleDragStart = (e: React.MouseEvent) => {
    e.preventDefault();
    isDragging.current = true;
    const startX = e.clientX;
    const startWidth = treeWidth;
    const onMove = (ev: MouseEvent) => {
      if (!isDragging.current) return;
      setTreeWidth(Math.min(520, Math.max(160, startWidth + ev.clientX - startX)));
    };
    const onUp = () => {
      isDragging.current = false;
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
  };

  useEffect(() => {
    const fetchDetail = async () => {
      if (!workerProductId) return;
      setLoading(true);
      setError("");
      try {
        const [productRes, versionsRes, cliInfoRes] = await Promise.all([
          APIs.getProduct({ id: workerProductId }),
          getWorkerVersions(workerProductId).catch(() => null),
          getWorkerCliInfo(workerProductId).catch(() => null),
        ]);
        if (productRes.code === "SUCCESS" && productRes.data) {
          setData(productRes.data);
          if (productRes.data.workerConfig) {
            setWorkerConfig(productRes.data.workerConfig);
          }
        } else {
          setError(productRes.message || "数据加载失败");
        }

        // Set CLI download info
        if (cliInfoRes?.code === "SUCCESS" && cliInfoRes.data) {
          setCliInfo(cliInfoRes.data);
        }

        // Only show online (published) versions in frontend
        const onlineVersions = (versionsRes?.code === "SUCCESS" && Array.isArray(versionsRes.data))
          ? versionsRes.data.filter((v: WorkerVersion) => v.status === "online")
          : [];
        setVersions(onlineVersions);

        // Default to latest online version
        const defaultVersion = onlineVersions[0]?.version;
        setSelectedVersion(defaultVersion);

        // Load file tree for the default version
        if (defaultVersion) {
          await loadVersionContent(defaultVersion);
        }
      } catch (err) {
        console.error("API请求失败:", err);
        setError("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [workerProductId]);

  const loadVersionContent = async (version?: string) => {
    if (!workerProductId) return;
    try {
      const filesRes = await getWorkerFileTree(workerProductId, version).catch(() => null);
      if (filesRes?.code === "SUCCESS" && Array.isArray(filesRes.data) && filesRes.data.length > 0) {
        setFileTree(filesRes.data);
        // Default select manifest.json
        setSelectedFilePath("manifest.json");
        setFileLoading(true);
        getWorkerFileContent(workerProductId, "manifest.json", version)
          .then((r) => { if (r.code === "SUCCESS" && r.data) setFileContent(r.data); })
          .catch(() => {})
          .finally(() => setFileLoading(false));
        // Fetch AGENTS.md for Overview tab: check root and config/ subdirectory
        const findAgentsMd = (nodes: WorkerFileTreeNode[]): WorkerFileTreeNode | null => {
          for (const n of nodes) {
            if (n.type === 'file' && (n.path === 'AGENTS.md' || n.path === 'config/AGENTS.md')) return n;
            if (n.children) { const f = findAgentsMd(n.children); if (f) return f; }
          }
          return null;
        };
        const agentsMdNode = findAgentsMd(filesRes.data);
        if (agentsMdNode) {
          setOverviewLoading(true);
          getWorkerFileContent(workerProductId, agentsMdNode.path, version)
            .then((r) => { setOverviewContent(r.code === "SUCCESS" && r.data ? r.data.content : null); })
            .catch(() => setOverviewContent(null))
            .finally(() => setOverviewLoading(false));
        } else {
          setOverviewContent(null);
        }
      } else {
        setFileTree([]);
        setFileContent(null);
        setSelectedFilePath(undefined);
        setOverviewContent(null);
      }
    } catch {
      setFileTree([]);
    }
  };

  const handleSelectFile = useCallback(async (path: string) => {
    if (!workerProductId) return;
    setSelectedFilePath(path);
    setMdRawMode(true);
    setFileLoading(true);
    try {
      const res = await getWorkerFileContent(workerProductId, path, selectedVersion);
      if (res.code === "SUCCESS" && res.data) {
        setFileContent(res.data);
      }
    } catch {
      setFileContent(null);
    } finally {
      setFileLoading(false);
    }
  }, [workerProductId, selectedVersion]);

  const handleVersionChange = useCallback(async (version: string) => {
    setSelectedVersion(version);
    setFileContent(null);
    setSelectedFilePath(undefined);
    await loadVersionContent(version);
  }, [workerProductId, data]);

  const [copiedCmd, setCopiedCmd] = useState(false);
  const [copiedHiclaw, setCopiedHiclaw] = useState(false);
  const [copiedHttp, setCopiedHttp] = useState(false);
  const [cliInfo, setCliInfo] = useState<WorkerCliInfo | null>(null);
  const [mdRawMode, setMdRawMode] = useState(true);
  const [hiclawPlatform, setHiclawPlatform] = useState<'unix' | 'windows'>('unix');
  const [installMethod, setInstallMethod] = useState<'nl' | 'script'>('script');

  const handleDownload = useCallback(() => {
    if (!workerProductId) return;
    const a = document.createElement("a");
    a.href = getWorkerPackageUrl(workerProductId, selectedVersion);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }, [workerProductId, selectedVersion]);

  if (loading) {
    return (
      <Layout>
        <div className="max-w-7xl mx-auto px-4 py-6">
          <DetailSkeleton />
        </div>
      </Layout>
    );
  }

  if (error || !data) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error || "Worker 不存在"} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  const { name, description } = data;
  const workerTags = workerConfig?.tags || [];
  const hasFiles = fileTree.length > 0;

  const renderFilePreview = () => {
    if (!selectedFilePath) {
      return (
        <div className="flex items-center justify-center h-full text-gray-400">
          <div className="text-center">
            <FileFilled className="text-5xl mb-3 text-gray-300" />
            <p className="text-sm text-gray-400">点击左侧文件查看内容</p>
          </div>
        </div>
      );
    }
    if (fileLoading) {
      return <div className="flex justify-center py-16"><div className="w-8 h-8 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" /></div>;
    }
    if (!fileContent) {
      return <div className="text-gray-400 text-center py-16 text-sm">加载失败</div>;
    }
    if (fileContent.encoding === "base64") {
      return (
        <div className="text-gray-400 text-center py-16 text-sm">二进制文件，不支持预览</div>
      );
    }
    if (selectedFilePath.endsWith(".md")) {
      const highlighted = (() => {
        try {
          if (hljs.getLanguage("markdown")) {
            return hljs.highlight(fileContent.content, { language: "markdown" }).value;
          }
          return hljs.highlightAuto(fileContent.content).value;
        } catch {
          return fileContent.content.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
        }
      })();
      const lineCount = fileContent.content.split("\n").length;
      const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";
      return (
        <div className="flex-1 overflow-auto bg-white h-full flex flex-col relative">
          {/* Toggle button - floats top-right */}
          <div className="absolute top-2 right-3 z-20">
            <Tooltip title={mdRawMode ? "渲染预览" : "源代码"}>
              <button
                onClick={() => setMdRawMode(!mdRawMode)}
                className="flex items-center gap-1 px-2 py-0.5 rounded text-xs text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
              >
                {mdRawMode ? <EyeOutlined /> : <CodeOutlined />}
                <span>{mdRawMode ? "Preview" : "Source"}</span>
              </button>
            </Tooltip>
          </div>
          {mdRawMode ? (
            <div className="flex flex-1 overflow-auto">
              <div
                className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none sticky left-0 bg-white z-10"
                style={{ fontFamily: codeFont, fontSize: "13px", lineHeight: "20px", borderRight: "1px solid #f0f0f0" }}
              >
                {Array.from({ length: lineCount }, (_, i) => (
                  <div key={i} className="text-gray-300">{i + 1}</div>
                ))}
              </div>
              <pre className="flex-1 py-3 pl-5 pr-4 m-0 bg-white" style={{ fontFamily: codeFont, fontSize: "13px", lineHeight: "20px" }}>
                <code className="hljs language-markdown" dangerouslySetInnerHTML={{ __html: highlighted }} />
              </pre>
            </div>
          ) : (
            <div className="flex-1 overflow-auto px-6 pb-6 pt-8">
              <MdPreview content={fileContent.content} />
            </div>
          )}
        </div>
      );
    }
    const lang = inferLanguage(selectedFilePath);
    const highlighted = (() => {
      try {
        if (lang && lang !== "plaintext" && hljs.getLanguage(lang)) {
          return hljs.highlight(fileContent.content, { language: lang }).value;
        }
        return hljs.highlightAuto(fileContent.content).value;
      } catch {
        return fileContent.content.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
      }
    })();

    const lineCount = fileContent.content.split("\n").length;
    const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";

    return (
      <div className="flex-1 overflow-auto bg-white h-full">
        <div className="flex min-h-full">
          <div
            className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none sticky left-0 bg-white z-10"
            style={{ fontFamily: codeFont, fontSize: "13px", lineHeight: "20px", borderRight: '1px solid #f0f0f0' }}
          >
            {Array.from({ length: lineCount }, (_, i) => (
              <div key={i} className="text-gray-300">{i + 1}</div>
            ))}
          </div>
          <pre
            className="flex-1 py-3 pl-5 pr-4 m-0 bg-white"
            style={{ fontFamily: codeFont, fontSize: "13px", lineHeight: "20px", whiteSpace: "pre", wordBreak: "normal" }}
          >
            <code
              className="hljs"
              style={{ background: "transparent", padding: 0 }}
              dangerouslySetInnerHTML={{ __html: highlighted }}
            />
          </pre>
        </div>
      </div>
    );
  };

  return (
    <Layout>
      <div className="py-8 flex flex-col gap-4">
        {/* Page header */}
        <div className="flex-shrink-0">
          <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-2 mb-4 px-4 py-2 rounded-xl text-gray-600 hover:text-colorPrimary hover:bg-colorPrimaryBgHover transition-all duration-200"
          >
            <ArrowLeftOutlined />
            <span>返回</span>
          </button>

          <div className="flex items-center gap-4 mb-3">
            <ProductIcon name={name} icon={data.icon} />
            <div className="flex-1 min-w-0">
              <h1 className="text-xl font-semibold text-gray-900 mb-1">{name}</h1>
              {data.updatedAt && (
                <div className="text-sm text-gray-400">
                  {new Date(data.updatedAt).toLocaleDateString("zh-CN", {
                    year: "numeric", month: "2-digit", day: "2-digit",
                  }).replace(/\//g, ".")} updated
                </div>
              )}
            </div>
          </div>

          <p className="text-gray-600 text-sm leading-relaxed">{description}</p>

          {workerTags.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-3">
              {workerTags.map((tag) => (
                <Tag key={tag} color="purple">{tag}</Tag>
              ))}
            </div>
          )}
        </div>

        {/* Main content */}
        <div className="flex flex-col lg:flex-row gap-4">
          {/* Left: file viewer with Overview / File tabs */}
          <div className="flex-1 min-w-0">
            <div className="bg-white rounded-lg overflow-hidden flex flex-col" style={{ height: 'calc(100vh - 280px)', minHeight: 500, border: '1px solid #f0f0f0' }}>
            {/* Tab header */}
            <div className="flex gap-6 px-4 pt-3 flex-shrink-0" style={{ borderBottom: '1px solid #f0f0f0' }}>
              <button
                className={`pb-2 text-sm font-medium transition-colors border-b-2 ${
                  activeTab === 'overview'
                    ? 'text-blue-600 border-blue-600'
                    : 'text-gray-500 border-transparent hover:text-gray-700'
                }`}
                onClick={() => setActiveTab('overview')}
              >
                Overview
              </button>
              <button
                className={`pb-2 text-sm font-medium transition-colors border-b-2 ${
                  activeTab === 'file'
                    ? 'text-blue-600 border-blue-600'
                    : 'text-gray-500 border-transparent hover:text-gray-700'
                }`}
                onClick={() => setActiveTab('file')}
              >
                File
              </button>
            </div>

            {/* Overview tab */}
            {activeTab === 'overview' && (
              <div className="flex-1 overflow-auto p-6">
                {overviewLoading ? (
                  <div className="flex justify-center pt-8"><div className="w-6 h-6 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" /></div>
                ) : overviewContent ? (
                  <MdPreview content={overviewContent} />
                ) : (
                  <div className="text-gray-400 text-sm text-center pt-8">
                    该版本未包含 AGENTS.md 文件
                  </div>
                )}
              </div>
            )}

            {/* File tab */}
            {activeTab === 'file' && (
              <div className="flex flex-1 min-h-0">
                {/* File tree */}
                <div className="bg-white overflow-y-auto overflow-x-hidden flex-shrink-0 p-2" style={{ width: treeWidth, borderRight: '1px solid #f0f0f0' }}>
                  {hasFiles ? (
                    <SkillFileTree
                      nodes={fileTree as any}
                      selectedPath={selectedFilePath}
                      onSelect={handleSelectFile}
                    />
                  ) : (
                    <div className="flex items-center justify-center h-full text-gray-400 text-sm">暂无文件</div>
                  )}
                </div>
                {/* Drag handle */}
                <div
                  onMouseDown={handleDragStart}
                  className="w-1 flex-shrink-0 cursor-col-resize hover:bg-blue-200 transition-colors bg-transparent"
                />
                {/* File preview */}
                <div className="flex-1 overflow-auto flex flex-col">
                  {renderFilePreview()}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right sidebar: download card */}
        <div className="w-full lg:w-[420px] flex-shrink-0 order-1 lg:order-2">
          <div className="bg-white rounded-lg overflow-hidden" style={{ border: '1px solid #f0f0f0' }}>
            {/* Card header: title + version selector */}
            <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: '1px solid #f0f0f0' }}>
              <span className="text-sm font-semibold text-gray-800">下载</span>
              <Select
                value={selectedVersion}
                onChange={handleVersionChange}
                size="large"
                placeholder="暂无版本"
                disabled={versions.length === 0}
                style={{ width: 180, fontSize: 15 }}
                options={versions.map((v) => ({
                  value: v.version,
                  label: (
                    <div className="flex items-center gap-1.5">
                      <span>{v.version}</span>
                      {v.version === versions[0]?.version && (
                        <Tag color="blue" className="!m-0 !text-xs !px-1.5 !py-0 !leading-5">latest</Tag>
                      )}
                    </div>
                  ),
                }))}
              />
            </div>

            {/* Action buttons */}
            <div className="px-4 py-3" style={{ borderBottom: '1px solid #f0f0f0' }}>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                onClick={handleDownload}
                disabled={versions.length === 0}
                block
                size="middle"
              >
                下载 Worker 包
              </Button>
            </div>

            {/* HiClaw 安装 */}
            {cliInfo && (
              <div className="px-4 py-3" style={{ borderBottom: '1px solid #f0f0f0' }}>
                <div className="flex items-center gap-1.5 mb-3">
                  <CloudUploadOutlined className="text-gray-400 text-xs" />
                  <span className="text-xs font-medium text-gray-500">安装到 HiClaw</span>
                </div>

                {/* 安装方式切换 Tab */}
                <div className="flex bg-gray-100 rounded-lg p-1 mb-3">
                  <button
                    onClick={() => setInstallMethod('script')}
                    className={`flex-1 py-2 text-xs rounded-md transition-all ${
                      installMethod === 'script'
                        ? 'bg-white text-gray-800 font-medium shadow-sm'
                        : 'text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    脚本命令
                  </button>
                  <button
                    onClick={() => setInstallMethod('nl')}
                    className={`flex-1 py-2 text-xs rounded-md transition-all ${
                      installMethod === 'nl'
                        ? 'bg-white text-gray-800 font-medium shadow-sm'
                        : 'text-gray-500 hover:text-gray-700'
                    }`}
                  >
                    自然语言
                  </button>
                </div>

                {/* 自然语言面板 */}
                {installMethod === 'nl' && (
                  <div>
                    <div className="flex items-center bg-gray-50 border border-dashed border-gray-300 rounded-lg px-4 py-3">
                      <div className="text-sm text-gray-700">
                        从 market 中导入 "{cliInfo.resourceName}" worker
                      </div>
                    </div>
                    <div className="text-xs text-gray-400 mt-2 ml-1">
                      在 HiClaw 聊天框中向 Manager 发送上述指令即可导入
                    </div>
                  </div>
                )}

                {/* 脚本命令面板 */}
                {installMethod === 'script' && (
                  <div>
                    <div className="flex gap-2 mb-2">
                      <button
                        onClick={() => setHiclawPlatform('unix')}
                        className={`text-xs px-2.5 py-1 rounded transition-colors ${
                          hiclawPlatform === 'unix'
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                        }`}
                      >
                        Linux / Mac
                      </button>
                      <button
                        onClick={() => setHiclawPlatform('windows')}
                        className={`text-xs px-2.5 py-1 rounded transition-colors ${
                          hiclawPlatform === 'windows'
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                        }`}
                      >
                        Windows
                      </button>
                      <button
                        onClick={() => {
                          const version = selectedVersion || 'v1';
                          const encodedName = encodeURIComponent(cliInfo.resourceName);
                          const hostPart = cliInfo.nacosPort ? `${cliInfo.nacosHost}:${cliInfo.nacosPort}` : cliInfo.nacosHost;
                          const selectedVersionInfo = versions.find((v) => v.version === version);
                          const isLatest = selectedVersionInfo?.isLatest ?? false;
                          const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                          const canOmitPackage = isDefaultHost && isLatest;
                          const versionPath = isLatest ? '' : `/${version}`;
                          const packageUrl = `nacos://${hostPart}/${cliInfo.namespace}/${encodedName}${versionPath}`;
                          const packageArg = canOmitPackage ? '' : ` --package "${packageUrl}"`;
                          const cmd = hiclawPlatform === 'unix'
                            ? `curl -fsSL https://higress.ai/hiclaw/import.sh | bash -s -- worker --name "${cliInfo.resourceName}"${packageArg}`
                            : `irm https://higress.ai/hiclaw/import.ps1 -OutFile import.ps1; .\\import.ps1 worker --name "${cliInfo.resourceName}"${packageArg}`;
                          copyToClipboard(cmd).then(() => {
                            setCopiedHiclaw(true);
                            setTimeout(() => setCopiedHiclaw(false), 2002);
                          });
                        }}
                        className="ml-auto text-xs text-gray-400 hover:text-gray-600 transition-colors"
                      >
                        {copiedHiclaw ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                      </button>
                    </div>
                    <div className="rounded-md bg-gray-100 border border-gray-200 px-3 py-2">
                      <code className="text-[12px] text-gray-700 break-all" style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}>
                        {(() => {
                          const version = selectedVersion || 'v1';
                          const encodedName = encodeURIComponent(cliInfo.resourceName);
                          const hostPart = cliInfo.nacosPort ? `${cliInfo.nacosHost}:${cliInfo.nacosPort}` : cliInfo.nacosHost;
                          const selectedVersionInfo = versions.find((v) => v.version === version);
                          const isLatest = selectedVersionInfo?.isLatest ?? false;
                          const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                          const canOmitPackage = isDefaultHost && isLatest;
                          const versionPath = isLatest ? '' : `/${version}`;
                          const packageUrl = `nacos://${hostPart}/${cliInfo.namespace}/${encodedName}${versionPath}`;
                          const packageArg = canOmitPackage ? '' : ` --package "${packageUrl}"`;
                          return hiclawPlatform === 'unix'
                            ? `curl -fsSL https://higress.ai/hiclaw/import.sh | bash -s -- worker --name "${cliInfo.resourceName}"${packageArg}`
                            : `irm https://higress.ai/hiclaw/import.ps1 -OutFile import.ps1; .\\import.ps1 worker --name "${cliInfo.resourceName}"${packageArg}`;
                        })()}
                      </code>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* HTTP 下载 */}
            {cliInfo && (
              <div className="px-4 py-3">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-1.5">
                    <CloudUploadOutlined className="text-gray-400 text-xs" />
                    <span className="text-xs font-medium text-gray-500">HTTP 下载</span>
                  </div>
                  <button
                    onClick={() => {
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionParam = selectedVersion && !isLatest ? `?version=${encodeURIComponent(selectedVersion)}` : '';
                      const url = `${window.location.origin}/api/v1/workers/${workerProductId}/download${versionParam}`;
                      copyToClipboard(url).then(() => {
                        setCopiedHttp(true);
                        setTimeout(() => setCopiedHttp(false), 2000);
                      });
                    }}
                    disabled={!selectedVersion}
                    className="text-xs text-gray-400 hover:text-gray-600 transition-colors disabled:opacity-50"
                  >
                    {copiedHttp ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                  </button>
                </div>
                <div className="rounded-md bg-gray-100 border border-gray-200 px-3 py-2">
                  <code className="text-[12px] text-gray-700 break-all" style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}>
                    {(() => {
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionParam = selectedVersion && !isLatest ? `?version=${encodeURIComponent(selectedVersion)}` : '';
                      return `${typeof window !== 'undefined' ? window.location.origin : ''}/api/v1/workers/${workerProductId}/download${versionParam}`;
                    })()}
                  </code>
                </div>
              </div>
            )}

            {/* Nacos CLI command */}
            {cliInfo && (
              <div className="px-4 py-3">
                {/* npx 下载 */}
                <div className="mb-3">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-1.5">
                      <CodeOutlined className="text-gray-400 text-xs" />
                      <span className="text-xs font-medium text-gray-500">NPX 下载</span>
                    </div>
                    <button
                      onClick={() => {
                        const quotedName = cliInfo.resourceName.includes(' ') ? `"${cliInfo.resourceName}"` : cliInfo.resourceName;
                        const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                        const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                        const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                        const cmd = `npx @nacos-group/cli${hostArg} agentspec-get ${quotedName}${versionArg}`;
                        copyToClipboard(cmd).then(() => {
                          setCopiedCmd(true);
                          setTimeout(() => setCopiedCmd(false), 2000);
                        });
                      }}
                      className="text-xs text-gray-400 hover:text-gray-600 transition-colors"
                    >
                      {copiedCmd ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                    </button>
                  </div>
                  <div className="rounded-md bg-gray-100 border border-gray-200 px-3 py-2">
                    <code className="text-[12px] text-gray-700 break-all" style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}>
                      {(() => {
                        const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                        const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                        const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                        return `npx @nacos-group/cli${hostArg} agentspec-get ${cliInfo.resourceName.includes(' ') ? `"${cliInfo.resourceName}"` : cliInfo.resourceName}${versionArg}`;
                      })()}
                    </code>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        </div>
      </div>
    </Layout>
  );
}

export default WorkerDetail;
