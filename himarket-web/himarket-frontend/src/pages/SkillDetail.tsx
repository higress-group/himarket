import { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useTranslation } from 'react-i18next';
import { Layout } from "../components/Layout";
import { Alert, Tag, Button, Select, Tooltip, Upload, Modal, message } from "antd";
import { ArrowLeftOutlined, DownloadOutlined, CopyOutlined, CheckOutlined, FileFilled, CodeOutlined, EyeOutlined, CloudUploadOutlined, ThunderboltOutlined, InboxOutlined } from "@ant-design/icons";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";
import type { IProductDetail } from "../lib/apis";
import type { ISkillConfig } from "../lib/apis/typing";
import type { SkillFileTreeNode, SkillFileContent, SkillVersion, SkillCliInfo } from "../lib/apis/cliProvider";
import APIs from "../lib/apis";
import { getSkillFiles, getSkillFileContent, getSkillPackageUrl, getSkillVersions, getSkillCliInfo } from "../lib/apis/cliProvider";
import { uploadDeveloperSkillPackage } from "../lib/apis/developerSkill";
import { parseSkillMd } from "../lib/skillMdUtils";
import { getIconString } from "../lib/iconUtils";
import { ProductIconRenderer } from "../components/icon/ProductIconRenderer";
import MarkdownRender from "../components/MarkdownRender";
import SkillFileTree from "../components/skill/SkillFileTree";
import RelatedSkills from "../components/skill/RelatedSkills";
import { copyToClipboard } from "../lib/utils";
import { SkillWorkerDetailSkeleton } from "../components/loading";
import { useAuth } from "../hooks/useAuth";
import { getDeveloperInfo } from "../lib/apis/developer";

type IdeType = 'qoder' | 'qoderwork' | 'claude' | 'codex' | 'cursor' | 'kiro' | 'lingma' | 'copaw' | 'openclaw';

const IDE_OPTIONS: { value: IdeType; label: string; icon: string }[] = [
  { value: 'copaw', label: 'CoPaw', icon: '/copaw.png' },
  { value: 'openclaw', label: 'OpenClaw', icon: '/openclaw.svg' },
  { value: 'qoder', label: 'Qoder', icon: 'https://g.alicdn.com/qbase/qoder/0.0.65/favIcon.svg' },
  { value: 'qoderwork', label: 'QoderWork', icon: 'https://img.alicdn.com/imgextra/i1/O1CN01clv0Oy1Tia1VN1WEO_!!6000000002416-1-tps-1200-1200.gif' },
  { value: 'claude', label: 'Claude', icon: 'https://img.alicdn.com/imgextra/i3/O1CN01JqyNKC1VmMU2MHdF9_!!6000000002695-55-tps-100-101.svg' },
  { value: 'codex', label: 'Codex', icon: 'https://img.alicdn.com/imgextra/i3/O1CN011DvgjK1s54F8K000Q_!!6000000005714-0-tps-248-248.jpg' },
  { value: 'cursor', label: 'Cursor', icon: 'https://aimg.alistatic.com/i/dm885X/UgW1Qzx0RPwrNIVs4sWlr-7b5511235d.svg' },
  { value: 'kiro', label: 'Kiro', icon: '/kiro.png' },
  { value: 'lingma', label: 'Lingma', icon: 'https://img.alicdn.com/imgextra/i2/O1CN01OR7j0c1OvKuJfKBAw_!!6000000001767-2-tps-280-280.png' },
];

const getDefaultOutputDir = (ide: IdeType): string => {
  const dirMap: Record<IdeType, string> = {
    qoder: '~/.qoder/skills',
    qoderwork: '~/.qoderwork/skills',
    claude: '~/.claude/skills',
    codex: '~/.codex/skills',
    cursor: '~/.cursor/skills',
    kiro: '~/.kiro/skills',
    lingma: '~/.lingma/skills',
    copaw: '~/.copaw/skill_pool',
    openclaw: '~/.openclaw/skills',
  };
  return dirMap[ide];
};

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
  const { t } = useTranslation('skillDetail');
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
  const [copiedHttp, setCopiedHttp] = useState(false);
  const [mdRawMode, setMdRawMode] = useState(true);
  const [versions, setVersions] = useState<SkillVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<string | undefined>();
  const [cliInfo, setCliInfo] = useState<SkillCliInfo | null>(null);
  const [selectedIde, setSelectedIde] = useState<IdeType>('copaw');
  const [outputDir, setOutputDir] = useState<string>('~/.copaw/skill_pool');
  const { isLoggedIn } = useAuth();
  const [reuploadOpen, setReuploadOpen] = useState(false);
  const [reuploadFile, setReuploadFile] = useState<File | null>(null);
  const [reuploading, setReuploading] = useState(false);
  const [currentDeveloperId, setCurrentDeveloperId] = useState<string | null>(null);

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
    if (!isLoggedIn) return;
    getDeveloperInfo().then((res) => {
      if (res.code === "SUCCESS" && res.data) {
        setCurrentDeveloperId(res.data.developerId);
      }
    }).catch(() => {});
  }, [isLoggedIn]);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!skillProductId) return;
      setLoading(true);
      setError("");
      try {
        const [productRes, versionsRes, cliInfoRes] = await Promise.all([
          APIs.getProduct({ id: skillProductId }),
          getSkillVersions(skillProductId).catch(() => null),
          getSkillCliInfo(skillProductId).catch(() => null),
        ]);
        if (productRes.code === "SUCCESS" && productRes.data) {
          setData(productRes.data);
          if (productRes.data.skillConfig) {
            setSkillConfig(productRes.data.skillConfig);
          }
        } else {
          setError(productRes.message || t('dataLoadFailed'));
        }

        // Set CLI download info
        if (cliInfoRes?.code === "SUCCESS" && cliInfoRes.data) {
          setCliInfo(cliInfoRes.data);
        }

        // Only show online (published) versions in frontend
        const allVersions = (versionsRes?.code === "SUCCESS" && Array.isArray(versionsRes.data))
          ? versionsRes.data
          : [];
        const onlineVersions = allVersions.filter((v: SkillVersion) => v.status === "online");
        setVersions(onlineVersions);

        // Default to latest online version
        const defaultVersion = onlineVersions[0]?.version;
        setSelectedVersion(defaultVersion);

        // Load file tree for the default version
        await loadVersionContent(defaultVersion);

        // Fallback: if no online versions but product has document, use it as overview
        if (onlineVersions.length === 0 && allVersions.length > 0 && productRes.data?.document) {
          setOverviewContent(productRes.data.document);
        }
      } catch (err) {
        console.error("API请求失败:", err);
        setError(t('loadFailed'));
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
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }, [skillProductId, selectedVersion]);

  const handleReupload = async () => {
    if (!skillProductId || !reuploadFile) return;
    setReuploading(true);
    try {
      await uploadDeveloperSkillPackage(skillProductId, reuploadFile);
      message.success(t('reuploadSuccess', 'Skill 压缩包重新上传成功'));
      setReuploadOpen(false);
      setReuploadFile(null);
      // Reload page data
      window.location.reload();
    } catch {
      message.error(t('reuploadFailed', '重新上传失败，请检查压缩包格式'));
    } finally {
      setReuploading(false);
    }
  };

  const isOwner = isLoggedIn && !!currentDeveloperId && data?.developerId === currentDeveloperId;

  if (loading) {
    return (
      <Layout>
        <SkillWorkerDetailSkeleton />
      </Layout>
    );
  }

  if (error || !data) {
    return (
      <Layout>
        <div className="p-8">
          <Alert message={t('error')} description={error || t('skillNotExist')} type="error" showIcon />
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
            <p className="text-sm text-gray-400">{t('clickFileToView')}</p>
          </div>
        </div>
      );
    }
    if (fileLoading) {
      return <div className="flex justify-center py-16"><div className="w-8 h-8 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" /></div>;
    }
    if (!fileContent) {
      return <div className="text-gray-400 text-center py-16 text-sm">{t('fileLoadFailed')}</div>;
    }
    if (fileContent.encoding === "base64") {
      return (
        <div className="text-gray-400 text-center py-16 text-sm">{t('binaryNotSupported')}</div>
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
            <Tooltip title={mdRawMode ? t('renderPreview') : t('sourceCode')}>
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
            <span>{t('back')}</span>
          </button>

          <div className="flex items-center gap-4 mb-3">
            {data.icon && data.icon.value ? (
              <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gradient-to-br from-colorPrimary/10 to-colorPrimary/5 border border-gray-200 overflow-hidden">
                <ProductIconRenderer className="w-full h-full object-cover" iconType={getIconString(data.icon)} />
              </div>
            ) : (
              <div className="w-16 h-16 rounded-xl flex-shrink-0 flex items-center justify-center bg-gray-50 border border-gray-200">
                <ThunderboltOutlined className="text-2xl" />
              </div>
            )}
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
                  <div className="flex justify-center pt-8"><div className="w-6 h-6 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" /></div>
                ) : overviewContent ? (
                  <SkillOverview content={overviewContent} />
                ) : (
                  <div className="text-gray-400 text-sm text-center pt-8">
                    {t('noSkillMd')}
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
                    <div className="flex items-center justify-center h-full text-gray-400 text-sm">{t('noFiles')}</div>
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
          {/* Re-upload card (owner only) */}
          {isOwner && (
            <div className="bg-white rounded-xl overflow-hidden shadow-sm" style={{ border: '1px solid #e8eaef' }}>
              <div className="px-4 py-3">
                <span className="text-sm font-semibold text-gray-800">{t('manageSkill', '管理 Skill')}</span>
              </div>
              <div className="px-4 pb-4">
                <Button
                  icon={<CloudUploadOutlined />}
                  onClick={() => setReuploadOpen(true)}
                  block
                >
                  {t('reuploadPackage', '重新上传压缩包')}
                </Button>
              </div>
            </div>
          )}

          <div className="bg-white rounded-xl overflow-hidden shadow-sm" style={{ border: '1px solid #e8eaef' }}>
            {/* Card header: title + version selector */}
            <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
              <span className="text-sm font-semibold text-gray-800">{t('download')}</span>
              <Select
                value={selectedVersion}
                onChange={handleVersionChange}
                size="large"
                placeholder={t('noVersion')}
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
            <div className="px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                onClick={handleDownload}
                disabled={versions.length === 0}
                block
                size="middle"
              >
                {t('downloadSkillPackage')}
              </Button>
            </div>

            {/* Nacos CLI command */}
            {cliInfo && (
              <div className="px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
                <div className="flex items-center gap-1.5 mb-3">
                  <CodeOutlined className="text-indigo-400/80 text-[13px]" />
                  <span className="text-xs font-semibold text-gray-600 tracking-wide">{t('npxDownload')}</span>
                </div>

                {/* IDE/Tool Selection */}
                <div className="mb-3">
                  <div className="flex flex-wrap gap-2">
                    {IDE_OPTIONS.map((ide) => (
                      <button
                        key={ide.value}
                        onClick={() => {
                          setSelectedIde(ide.value);
                          setOutputDir(getDefaultOutputDir(ide.value));
                        }}
                        className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border text-xs transition-all ${
                          selectedIde === ide.value
                            ? 'border-blue-500 bg-blue-50 text-blue-700'
                            : 'border-gray-200 hover:border-gray-300 text-gray-600'
                        }`}
                      >
                        {ide.icon && (
                          <img
                            src={ide.icon}
                            alt={ide.label}
                            className="w-4 h-4 object-contain"
                          />
                        )}
                        <span>{ide.label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Output Directory */}
                <div className="mb-3">
                  <div className="text-xs font-medium text-gray-600 mb-1.5">{t('outputDir')}</div>
                  <input
                    type="text"
                    value={outputDir}
                    onChange={(e) => setOutputDir(e.target.value)}
                    className="w-full px-3 py-2 text-xs border border-gray-200 rounded-md focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 bg-white text-gray-700"
                    placeholder={t('outputDirPlaceholder')}
                  />
                </div>

                <div className="relative rounded-md bg-gray-50/80 border border-gray-200 border-l-[2.5px] border-l-indigo-300/60 pl-3 pr-9 py-2.5">
                  <button
                    onClick={() => {
                      const quotedName = cliInfo.resourceName.includes(' ') ? `"${cliInfo.resourceName}"` : cliInfo.resourceName;
                      const quotedDir = outputDir.includes(' ') ? `"${outputDir}"` : outputDir;
                      const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                      const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                      const cmd = `npx @nacos-group/cli${hostArg} skill-get ${quotedName} -o ${quotedDir}${versionArg}`;
                      copyToClipboard(cmd).then(() => {
                        setCopied(true);
                        setTimeout(() => setCopied(false), 2000);
                      });
                    }}
                    className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all"
                  >
                    {copied ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                  </button>
                  <code className="text-[12px] text-gray-700 break-all" style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}>
                    {(() => {
                      const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                      const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                      return `npx @nacos-group/cli${hostArg} skill-get ${cliInfo.resourceName.includes(' ') ? `"${cliInfo.resourceName}"` : cliInfo.resourceName} -o ${outputDir.includes(' ') ? `"${outputDir}"` : outputDir}${versionArg}`;
                    })()}
                  </code>
                </div>
              </div>
            )}

            {/* HTTP 下载 */}
            {cliInfo && (
              <div className="px-4 py-3">
                <div className="flex items-center gap-1.5 mb-2">
                  <CloudUploadOutlined className="text-indigo-400/80 text-[13px]" />
                  <span className="text-xs font-semibold text-gray-600 tracking-wide">{t('httpDownload')}</span>
                </div>
                <div className="relative rounded-md bg-gray-50/80 border border-gray-200 border-l-[2.5px] border-l-indigo-300/60 pl-3 pr-9 py-2.5">
                  <button
                    onClick={() => {
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionParam = selectedVersion && !isLatest ? `?version=${encodeURIComponent(selectedVersion)}` : '';
                      const url = `${window.location.origin}/api/v1/skills/${skillProductId}/download${versionParam}`;
                      copyToClipboard(url).then(() => {
                        setCopiedHttp(true);
                        setTimeout(() => setCopiedHttp(false), 2000);
                      });
                    }}
                    disabled={!selectedVersion}
                    className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all disabled:opacity-50"
                  >
                    {copiedHttp ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                  </button>
                  <code className="text-[12px] text-gray-700 break-all" style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}>
                    {(() => {
                      const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
                      const isLatest = selectedVersionInfo?.isLatest ?? false;
                      const versionParam = selectedVersion && !isLatest ? `?version=${encodeURIComponent(selectedVersion)}` : '';
                      return `${typeof window !== 'undefined' ? window.location.origin : ''}/api/v1/skills/${skillProductId}/download${versionParam}`;
                    })()}
                  </code>
                </div>
              </div>
            )}
          </div>

          <RelatedSkills currentProductId={skillProductId!} currentSkillTags={skillConfig?.skillTags} />
        </div>
      </div>
      </div>

      {/* Re-upload Modal */}
      <Modal
        title={t('reuploadTitle', '重新上传 Skill 压缩包')}
        open={reuploadOpen}
        onCancel={() => { setReuploadOpen(false); setReuploadFile(null); }}
        footer={[
          <Button key="cancel" onClick={() => { setReuploadOpen(false); setReuploadFile(null); }} disabled={reuploading}>
            {t('cancel', '取消')}
          </Button>,
          <Button key="submit" type="primary" loading={reuploading} onClick={handleReupload} disabled={!reuploadFile}>
            {t('upload', '上传')}
          </Button>,
        ]}
        width={480}
        destroyOnClose
      >
        <Upload.Dragger
          accept=".zip"
          maxCount={1}
          beforeUpload={(file) => {
            setReuploadFile(file);
            return false;
          }}
          onRemove={() => setReuploadFile(null)}
          fileList={reuploadFile ? [{ uid: '-1', name: reuploadFile.name, status: 'done' as const }] : []}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">{t('uploadDragHint', '点击或拖拽 .zip 文件到此处')}</p>
        </Upload.Dragger>
      </Modal>
    </Layout>
  );
}

export default SkillDetail;
