import { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import { Alert, Spin, Tag, Button, Select, Tooltip } from "antd";
import { ArrowLeftOutlined, DownloadOutlined, CopyOutlined, CheckOutlined, FileFilled, CodeOutlined, EyeOutlined } from "@ant-design/icons";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";
import type { IProductDetail } from "../lib/apis";
import type { ISkillConfig } from "../lib/apis/typing";
import type { SkillFileTreeNode, SkillFileContent, SkillVersion } from "../lib/apis/cliProvider";
import APIs from "../lib/apis";
import { getSkillFiles, getSkillFileContent, getSkillPackageUrl, getSkillVersions } from "../lib/apis/cliProvider";
import { parseSkillMd } from "../lib/skillMdUtils";
import MarkdownRender from "../components/MarkdownRender";
import SkillFileTree from "../components/skill/SkillFileTree";
import RelatedSkills from "../components/skill/RelatedSkills";

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

function SkillOverview({ content }: { content: string }) {
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

function SkillDetail() {
  const { skillProductId } = useParams<{ skillProductId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<IProductDetail>();
  const [skillConfig, setSkillConfig] = useState<ISkillConfig>();

  const [fileTree, setFileTree] = useState<SkillFileTreeNode[]>([]);
  const [selectedFilePath, setSelectedFilePath] = useState<string | undefined>();
  const [fileContent, setFileContent] = useState<SkillFileContent | null>(null);
  const [fileLoading, setFileLoading] = useState(false);
  const [treeWidth, setTreeWidth] = useState(224);
  const isDragging = useRef(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'file'>('overview');
  const [overviewContent, setOverviewContent] = useState<string | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [copiedCmd, setCopiedCmd] = useState(false);
  const [mdRawMode, setMdRawMode] = useState(true);
  const [versions, setVersions] = useState<SkillVersion[]>([]);
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
      if (!skillProductId) return;
      setLoading(true);
      setError("");
      try {
        const [productRes, versionsRes] = await Promise.all([
          APIs.getProduct({ id: skillProductId }),
          getSkillVersions(skillProductId).catch(() => null),
        ]);
        if (productRes.code === "SUCCESS" && productRes.data) {
          setData(productRes.data);
          if (productRes.data.skillConfig) {
            setSkillConfig(productRes.data.skillConfig);
          }
        } else {
          setError(productRes.message || "数据加载失败");
        }

        // Only show online (published) versions in frontend
        const onlineVersions = (versionsRes?.code === "SUCCESS" && Array.isArray(versionsRes.data))
          ? versionsRes.data.filter((v: SkillVersion) => v.status === "online")
          : [];
        setVersions(onlineVersions);

        // Default to latest online version
        const defaultVersion = onlineVersions[0]?.version;
        setSelectedVersion(defaultVersion);

        // Load file tree for the default version
        await loadVersionContent(defaultVersion);
      } catch (err) {
        console.error("API请求失败:", err);
        setError("加载失败，请稍后重试");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [skillProductId]);

  const loadVersionContent = async (version?: string) => {
    if (!skillProductId) return;
    try {
      const filesRes = await getSkillFiles(skillProductId, version).catch(() => null);
      if (filesRes?.code === "SUCCESS" && Array.isArray(filesRes.data) && filesRes.data.length > 0) {
        const nodes = filesRes.data;
        setFileTree(nodes);
        const hasSkillMd = nodes.some((n: SkillFileTreeNode) => n.path === "SKILL.md");
        if (hasSkillMd) {
          setSelectedFilePath("SKILL.md");
          setFileLoading(true);
          setOverviewLoading(true);
          getSkillFileContent(skillProductId, "SKILL.md", version)
            .then((r) => {
              if (r.code === "SUCCESS" && r.data) {
                setFileContent(r.data);
                setOverviewContent(r.data.content);
              }
            })
            .catch(() => {})
            .finally(() => {
              setFileLoading(false);
              setOverviewLoading(false);
            });
        } else {
          setOverviewContent(null);
          setSelectedFilePath(undefined);
          setFileContent(null);
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

  const handleVersionChange = useCallback(async (version: string) => {
    setSelectedVersion(version);
    setFileContent(null);
    setSelectedFilePath(undefined);
    await loadVersionContent(version);
  }, [skillProductId]);

  const handleSelectFile = useCallback(async (path: string) => {
    if (!skillProductId) return;
    setSelectedFilePath(path);
    setMdRawMode(true);
    setFileLoading(true);
    try {
      const res = await getSkillFileContent(skillProductId, path, selectedVersion);
      if (res.code === "SUCCESS" && res.data) {
        setFileContent(res.data);
      }
    } catch {
      setFileContent(null);
    } finally {
      setFileLoading(false);
    }
  }, [skillProductId, selectedVersion]);

  const handleDownload = useCallback(() => {
    if (!skillProductId) return;
    const a = document.createElement("a");
    a.href = getSkillPackageUrl(skillProductId, selectedVersion);
    a.download = "";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }, [skillProductId, selectedVersion]);

  const handleCopyLink = useCallback(() => {
    if (!skillProductId) return;
    const url = `${window.location.origin}${getSkillPackageUrl(skillProductId, selectedVersion)}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [skillProductId, selectedVersion]);

  if (loading) {
    return (
      <Layout>
        <div className="flex justify-center items-center h-screen">
          <Spin size="large" tip="加载中..." />
        </div>
      </Layout>
    );
  }

  if (error || !data) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message="错误" description={error || "技能不存在"} type="error" showIcon />
        </div>
      </Layout>
    );
  }

  const { name, description } = data;
  const skillTags = skillConfig?.skillTags || [];
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
      return <div className="flex justify-center py-16"><Spin /></div>;
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
              <SkillOverview content={fileContent.content} />
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
            <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-purple-50 to-indigo-50 border border-purple-200">
              <span className="text-3xl">⚡</span>
            </div>
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

          <p className="text-gray-600 text-sm leading-relaxed mb-3">{description}</p>

          {skillTags.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {skillTags.map((tag) => (
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
                  <div className="flex justify-center pt-8"><Spin size="small" /></div>
                ) : overviewContent ? (
                  <SkillOverview content={overviewContent} />
                ) : (
                  <div className="text-gray-400 text-sm text-center pt-8">
                    该技能包未包含 SKILL.md 文件
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
                      nodes={fileTree}
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

        {/* Right sidebar: download card + related */}
        <div className="w-full lg:w-[420px] flex-shrink-0 order-1 lg:order-2 space-y-3">
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
            <div className="px-4 py-3 space-y-2" style={{ borderBottom: '1px solid #f0f0f0' }}>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                onClick={handleDownload}
                disabled={!selectedVersion}
                block
                size="middle"
              >
                下载 Skill 包
              </Button>
              <Button
                icon={copied ? <CheckOutlined /> : <CopyOutlined />}
                onClick={handleCopyLink}
                disabled={!selectedVersion}
                block
                size="middle"
                className={copied ? "!text-green-600 !border-green-400" : ""}
              >
                {copied ? "已复制链接" : "复制下载链接"}
              </Button>
            </div>

            {/* Nacos CLI command */}
            <div className="px-4 py-3">
              <div className="flex items-center gap-1.5 mb-2">
                <CodeOutlined className="text-gray-400 text-xs" />
                <span className="text-xs font-medium text-gray-500">Nacos 下载命令</span>
              </div>
              <div className="relative group rounded-md bg-gray-50 border border-gray-200">
                <pre
                  className="text-[12px] text-gray-700 px-3 py-2.5 pr-8 m-0 overflow-x-auto"
                  style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace", lineHeight: '1.6', whiteSpace: 'pre' }}
                >
                  {selectedVersion
                    ? `nacos-cli skill pull ${name} --version ${selectedVersion}`
                    : `nacos-cli skill pull ${name}`}
                </pre>
                <Tooltip title={copiedCmd ? "已复制" : "复制命令"}>
                  <button
                    onClick={() => {
                      const cmd = selectedVersion
                        ? `nacos-cli skill pull ${name} --version ${selectedVersion}`
                        : `nacos-cli skill pull ${name}`;
                      navigator.clipboard.writeText(cmd).then(() => {
                        setCopiedCmd(true);
                        setTimeout(() => setCopiedCmd(false), 2000);
                      });
                    }}
                    className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-gray-600 hover:bg-gray-200 transition-colors opacity-0 group-hover:opacity-100"
                  >
                    {copiedCmd ? <CheckOutlined className="text-green-500 text-xs" /> : <CopyOutlined className="text-xs" />}
                  </button>
                </Tooltip>
              </div>
            </div>
          </div>

          <RelatedSkills currentProductId={skillProductId!} />
        </div>
      </div>
      </div>
    </Layout>
  );
}

export default SkillDetail;
