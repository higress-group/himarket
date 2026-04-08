import {
  ArrowLeftOutlined,
  DownloadOutlined,
  CopyOutlined,
  CheckOutlined,
  UserOutlined,
  FileFilled,
  CodeOutlined,
  EyeOutlined,
  CloudUploadOutlined,
} from '@ant-design/icons';
import { Alert, Button, Select, Tag, Tooltip } from 'antd';
import hljs from 'highlight.js';
import { useEffect, useState, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';

import { Layout } from '../components/Layout';
import 'highlight.js/styles/github.css';
import { SkillWorkerDetailSkeleton } from '../components/loading';
import MarkdownRender from '../components/MarkdownRender';
import SkillFileTree from '../components/skill/SkillFileTree';
import APIs from '../lib/apis';
import {
  getWorkerFileTree,
  getWorkerFileContent,
  getWorkerVersions,
  getWorkerPackageUrl,
  getWorkerCliInfo,
} from '../lib/apis/workerTemplateApi';
import { parseSkillMd } from '../lib/skillMdUtils';
import { copyToClipboard } from '../lib/utils';

import type { IProductDetail } from '../lib/apis';
import type { SkillFileTreeNode } from '../lib/apis/cliProvider';
import type { IProductIcon } from '../lib/apis/typing';
import type { IWorkerConfig } from '../lib/apis/typing';
import type {
  WorkerFileTreeNode,
  WorkerFileContent,
  WorkerVersion,
  WorkerCliInfo,
} from '../lib/apis/workerTemplateApi';

function MdPreview({ content }: { content: string }) {
  const { body, frontmatter } = parseSkillMd(content);
  const fmEntries = Object.entries(frontmatter);
  return (
    <div className="markdown-body text-sm">
      {fmEntries.length > 0 && (
        <table className="mb-6 w-full text-[13px] border-collapse">
          <thead>
            <tr className="bg-[#f6f8fa]">
              {fmEntries.map(([k]) => (
                <th
                  className="border border-[#d0d7de] px-3 py-1.5 text-left font-semibold text-[#1f2328]"
                  key={k}
                >
                  {k}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              {fmEntries.map(([k, v]) => (
                <td
                  className="border border-[#d0d7de] px-3 py-1.5 text-[#1f2328] align-top"
                  key={k}
                >
                  {v}
                </td>
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
  const fileName = path.split('/').pop()?.toLowerCase() ?? '';
  if (fileName === 'dockerfile') return 'dockerfile';
  const ext = path.split('.').pop()?.toLowerCase() ?? '';
  const map: Record<string, string> = {
    bash: 'bash',
    c: 'c',
    cfg: 'ini',
    cpp: 'cpp',
    css: 'css',
    go: 'go',
    h: 'c',
    hpp: 'cpp',
    html: 'xml',
    ini: 'ini',
    java: 'java',
    js: 'javascript',
    json: 'json',
    jsx: 'javascript',
    kt: 'kotlin',
    md: 'markdown',
    py: 'python',
    rb: 'ruby',
    rs: 'rust',
    sh: 'bash',
    sql: 'sql',
    swift: 'swift',
    toml: 'ini',
    ts: 'typescript',
    tsx: 'typescript',
    xml: 'xml',
    yaml: 'yaml',
    yml: 'yaml',
  };
  return map[ext] ?? 'plaintext';
}

function getIconUrl(icon?: IProductIcon): string | null {
  if (!icon) return null;
  if (icon.type === 'URL' && icon.value) return icon.value;
  if (icon.type === 'BASE64' && icon.value) {
    return icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
  }
  return null;
}

function ProductIcon({ icon, name }: { name: string; icon?: IProductIcon }) {
  const iconUrl = getIconUrl(icon);

  if (iconUrl) {
    return (
      <img
        alt={name}
        className="w-16 h-16 rounded-xl flex-shrink-0 object-cover border border-gray-200"
        onError={(e) => {
          (e.target as HTMLImageElement).style.display = 'none';
        }}
        src={iconUrl}
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
  const { t } = useTranslation('workerDetail');
  const { workerProductId } = useParams<{ workerProductId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
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
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const loadVersionContent = useCallback(
    async (version?: string) => {
      if (!workerProductId) return;
      try {
        const filesRes = await getWorkerFileTree(workerProductId, version).catch(() => null);
        if (
          filesRes?.code === 'SUCCESS' &&
          Array.isArray(filesRes.data) &&
          filesRes.data.length > 0
        ) {
          setFileTree(filesRes.data);
          // Default select manifest.json
          setSelectedFilePath('manifest.json');
          setFileLoading(true);
          getWorkerFileContent(workerProductId, 'manifest.json', version)
            .then((r) => {
              if (r.code === 'SUCCESS' && r.data) setFileContent(r.data);
            })
            .catch(() => {})
            .finally(() => setFileLoading(false));
          // Fetch AGENTS.md for Overview tab: check root and config/ subdirectory
          const findAgentsMd = (nodes: WorkerFileTreeNode[]): WorkerFileTreeNode | null => {
            for (const n of nodes) {
              if (n.type === 'file' && (n.path === 'AGENTS.md' || n.path === 'config/AGENTS.md'))
                return n;
              if (n.children) {
                const f = findAgentsMd(n.children);
                if (f) return f;
              }
            }
            return null;
          };
          const agentsMdNode = findAgentsMd(filesRes.data);
          if (agentsMdNode) {
            setOverviewLoading(true);
            getWorkerFileContent(workerProductId, agentsMdNode.path, version)
              .then((r) => {
                setOverviewContent(r.code === 'SUCCESS' && r.data ? r.data.content : null);
              })
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
    },
    [workerProductId],
  );

  useEffect(() => {
    const fetchDetail = async () => {
      if (!workerProductId) return;
      setLoading(true);
      setError('');
      try {
        const [productRes, versionsRes, cliInfoRes] = await Promise.all([
          APIs.getProduct({ id: workerProductId }),
          getWorkerVersions(workerProductId).catch(() => null),
          getWorkerCliInfo(workerProductId).catch(() => null),
        ]);
        if (productRes.code === 'SUCCESS' && productRes.data) {
          setData(productRes.data);
          if (productRes.data.workerConfig) {
            setWorkerConfig(productRes.data.workerConfig);
          }
        } else {
          setError(productRes.message || t('dataLoadFailed'));
        }

        // Set CLI download info
        if (cliInfoRes?.code === 'SUCCESS' && cliInfoRes.data) {
          setCliInfo(cliInfoRes.data);
        }

        // Only show online (published) versions in frontend
        const onlineVersions =
          versionsRes?.code === 'SUCCESS' && Array.isArray(versionsRes.data)
            ? versionsRes.data.filter((v: WorkerVersion) => v.status === 'online')
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
        console.error('API请求失败:', err);
        setError(t('loadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [workerProductId, t, loadVersionContent]);

  const handleSelectFile = useCallback(
    async (path: string) => {
      if (!workerProductId) return;
      setSelectedFilePath(path);
      setMdRawMode(true);
      setFileLoading(true);
      try {
        const res = await getWorkerFileContent(workerProductId, path, selectedVersion);
        if (res.code === 'SUCCESS' && res.data) {
          setFileContent(res.data);
        }
      } catch {
        setFileContent(null);
      } finally {
        setFileLoading(false);
      }
    },
    [workerProductId, selectedVersion],
  );

  const handleVersionChange = useCallback(
    async (version: string) => {
      setSelectedVersion(version);
      setFileContent(null);
      setSelectedFilePath(undefined);
      await loadVersionContent(version);
    },
    [loadVersionContent],
  );

  const [copiedCmd, setCopiedCmd] = useState(false);
  const [copiedHiclaw, setCopiedHiclaw] = useState(false);
  const [copiedNl, setCopiedNl] = useState(false);
  const [copiedHttp, setCopiedHttp] = useState(false);
  const [cliInfo, setCliInfo] = useState<WorkerCliInfo | null>(null);
  const [mdRawMode, setMdRawMode] = useState(true);
  const [hiclawPlatform, setHiclawPlatform] = useState<'unix' | 'windows'>('unix');
  const [installMethod, setInstallMethod] = useState<'nl' | 'script'>('nl');

  const handleDownload = useCallback(() => {
    if (!workerProductId) return;
    const a = document.createElement('a');
    a.href = getWorkerPackageUrl(workerProductId, selectedVersion);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }, [workerProductId, selectedVersion]);

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
          <Alert
            description={error || t('workerNotExist')}
            message={t('error')}
            showIcon
            type="error"
          />
        </div>
      </Layout>
    );
  }

  const { description, name } = data;
  const workerTags = workerConfig?.tags || [];
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
      return (
        <div className="flex justify-center py-16">
          <div className="w-8 h-8 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" />
        </div>
      );
    }
    if (!fileContent) {
      return <div className="text-gray-400 text-center py-16 text-sm">{t('fileLoadFailed')}</div>;
    }
    if (fileContent.encoding === 'base64') {
      return (
        <div className="text-gray-400 text-center py-16 text-sm">{t('binaryNotSupported')}</div>
      );
    }
    if (selectedFilePath.endsWith('.md')) {
      const highlighted = (() => {
        try {
          if (hljs.getLanguage('markdown')) {
            return hljs.highlight(fileContent.content, { language: 'markdown' }).value;
          }
          return hljs.highlightAuto(fileContent.content).value;
        } catch {
          return fileContent.content
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        }
      })();
      const lineCount = fileContent.content.split('\n').length;
      const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";
      return (
        <div className="flex-1 overflow-auto bg-white h-full flex flex-col relative">
          {/* Toggle button - floats top-right */}
          <div className="absolute top-2 right-3 z-20">
            <Tooltip title={mdRawMode ? t('renderPreview') : t('sourceCode')}>
              <button
                className="flex items-center gap-1 px-2 py-0.5 rounded text-xs text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
                onClick={() => setMdRawMode(!mdRawMode)}
              >
                {mdRawMode ? <EyeOutlined /> : <CodeOutlined />}
                <span>{mdRawMode ? 'Preview' : 'Source'}</span>
              </button>
            </Tooltip>
          </div>
          {mdRawMode ? (
            <div className="flex flex-1 overflow-auto">
              <div
                className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none sticky left-0 bg-white z-10"
                style={{
                  borderRight: '1px solid #f0f0f0',
                  fontFamily: codeFont,
                  fontSize: '13px',
                  lineHeight: '20px',
                }}
              >
                {Array.from({ length: lineCount }, (_, i) => (
                  <div className="text-gray-300" key={i}>
                    {i + 1}
                  </div>
                ))}
              </div>
              <pre
                className="flex-1 py-3 pl-5 pr-4 m-0 bg-white"
                style={{ fontFamily: codeFont, fontSize: '13px', lineHeight: '20px' }}
              >
                <code
                  className="hljs language-markdown"
                  dangerouslySetInnerHTML={{ __html: highlighted }}
                />
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
        if (lang && lang !== 'plaintext' && hljs.getLanguage(lang)) {
          return hljs.highlight(fileContent.content, { language: lang }).value;
        }
        return hljs.highlightAuto(fileContent.content).value;
      } catch {
        return fileContent.content
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;');
      }
    })();

    const lineCount = fileContent.content.split('\n').length;
    const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";

    return (
      <div className="flex-1 overflow-auto bg-white h-full">
        <div className="flex min-h-full">
          <div
            className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none sticky left-0 bg-white z-10"
            style={{
              borderRight: '1px solid #f0f0f0',
              fontFamily: codeFont,
              fontSize: '13px',
              lineHeight: '20px',
            }}
          >
            {Array.from({ length: lineCount }, (_, i) => (
              <div className="text-gray-300" key={i}>
                {i + 1}
              </div>
            ))}
          </div>
          <pre
            className="flex-1 py-3 pl-5 pr-4 m-0 bg-white"
            style={{
              fontFamily: codeFont,
              fontSize: '13px',
              lineHeight: '20px',
              whiteSpace: 'pre',
              wordBreak: 'normal',
            }}
          >
            <code
              className="hljs"
              dangerouslySetInnerHTML={{ __html: highlighted }}
              style={{ background: 'transparent', padding: 0 }}
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
            className="flex items-center gap-2 mb-4 px-4 py-2 rounded-xl text-gray-600 hover:text-colorPrimary hover:bg-colorPrimaryBgHover transition-all duration-200"
            onClick={() => navigate(-1)}
          >
            <ArrowLeftOutlined />
            <span>{t('back')}</span>
          </button>

          <div className="flex items-center gap-4 mb-3">
            <ProductIcon icon={data.icon} name={name} />
            <div className="flex-1 min-w-0">
              <h1 className="text-xl font-semibold text-gray-900 mb-1">{name}</h1>
              {data.updatedAt && (
                <div className="text-sm text-gray-400">
                  {new Date(data.updatedAt)
                    .toLocaleDateString('zh-CN', {
                      day: '2-digit',
                      month: '2-digit',
                      year: 'numeric',
                    })
                    .replace(/\//g, '.')}{' '}
                  updated
                </div>
              )}
            </div>
          </div>

          <p className="text-gray-600 text-sm leading-relaxed">{description}</p>

          {workerTags.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-3">
              {workerTags.map((tag) => (
                <Tag color="purple" key={tag}>
                  {tag}
                </Tag>
              ))}
            </div>
          )}
        </div>

        {/* Main content */}
        <div className="flex flex-col lg:flex-row gap-4">
          {/* Left: file viewer with Overview / File tabs */}
          <div className="flex-1 min-w-0">
            <div
              className="bg-white rounded-lg overflow-hidden flex flex-col"
              style={{ border: '1px solid #f0f0f0', height: 'calc(100vh - 280px)', minHeight: 500 }}
            >
              {/* Tab header */}
              <div
                className="flex gap-6 px-4 pt-3 flex-shrink-0"
                style={{ borderBottom: '1px solid #f0f0f0' }}
              >
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
                    <div className="flex justify-center pt-8">
                      <div className="w-6 h-6 border-2 border-gray-200 border-t-blue-500 rounded-full animate-spin" />
                    </div>
                  ) : overviewContent ? (
                    <MdPreview content={overviewContent} />
                  ) : (
                    <div className="text-gray-400 text-sm text-center pt-8">{t('noAgentsMd')}</div>
                  )}
                </div>
              )}

              {/* File tab */}
              {activeTab === 'file' && (
                <div className="flex flex-1 min-h-0">
                  {/* File tree */}
                  <div
                    className="bg-white overflow-y-auto overflow-x-hidden flex-shrink-0 p-2"
                    style={{ borderRight: '1px solid #f0f0f0', width: treeWidth }}
                  >
                    {hasFiles ? (
                      <SkillFileTree
                        nodes={fileTree as unknown as SkillFileTreeNode[]}
                        onSelect={handleSelectFile}
                        selectedPath={selectedFilePath}
                      />
                    ) : (
                      <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                        {t('noFiles')}
                      </div>
                    )}
                  </div>
                  {/* Drag handle */}
                  <div
                    aria-hidden="true"
                    className="w-1 flex-shrink-0 cursor-col-resize hover:bg-blue-200 transition-colors bg-transparent"
                    onMouseDown={handleDragStart}
                    role="separator"
                  />
                  {/* File preview */}
                  <div className="flex-1 overflow-auto flex flex-col">{renderFilePreview()}</div>
                </div>
              )}
            </div>
          </div>

          {/* Right sidebar: download card */}
          <div className="w-full lg:w-[420px] flex-shrink-0 order-1 lg:order-2 lg:sticky lg:top-4 lg:self-start">
            <div
              className="bg-white rounded-xl overflow-hidden shadow-sm"
              style={{ border: '1px solid #e8eaef' }}
            >
              {/* Card header: title + version selector */}
              <div
                className="flex items-center justify-between px-4 py-3"
                style={{ borderBottom: '1px solid #edeef3' }}
              >
                <span className="text-sm font-semibold text-gray-800">{t('download')}</span>
                <Select
                  disabled={versions.length === 0}
                  onChange={handleVersionChange}
                  options={versions.map((v) => ({
                    label: (
                      <div className="flex items-center gap-1.5">
                        <span>{v.version}</span>
                        {v.version === versions[0]?.version && (
                          <Tag className="!m-0 !text-xs !px-1.5 !py-0 !leading-5" color="blue">
                            latest
                          </Tag>
                        )}
                      </div>
                    ),
                    value: v.version,
                  }))}
                  placeholder={t('noVersion')}
                  size="large"
                  style={{ fontSize: 15, width: 180 }}
                  value={selectedVersion}
                />
              </div>

              {/* Action buttons */}
              <div className="px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
                <Button
                  block
                  disabled={versions.length === 0}
                  icon={<DownloadOutlined />}
                  onClick={handleDownload}
                  size="middle"
                  type="primary"
                >
                  {t('downloadWorkerPackage')}
                </Button>
              </div>

              {/* HiClaw 安装 */}
              {cliInfo && (
                <div className="px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
                  <div className="flex items-center gap-1.5 mb-3">
                    <CloudUploadOutlined className="text-indigo-400/80 text-[13px]" />
                    <span className="text-xs font-semibold text-gray-600 tracking-wide">
                      {t('installToHiClaw')}
                    </span>
                    <span className="text-[10px] text-amber-600 bg-amber-50 border border-amber-200 rounded px-1.5 py-0.5 ml-auto">
                      {t('requiresHiClaw')}
                    </span>
                  </div>

                  {/* 安装方式切换 Tab */}
                  <div className="flex bg-gray-100 rounded-lg p-1 mb-3">
                    <button
                      className={`flex-1 py-2 text-xs rounded-md transition-all ${
                        installMethod === 'nl'
                          ? 'bg-white text-gray-800 font-medium shadow-sm'
                          : 'text-gray-500 hover:text-gray-700'
                      }`}
                      onClick={() => setInstallMethod('nl')}
                    >
                      {t('naturalLanguage')}
                    </button>
                    <button
                      className={`flex-1 py-2 text-xs rounded-md transition-all ${
                        installMethod === 'script'
                          ? 'bg-white text-gray-800 font-medium shadow-sm'
                          : 'text-gray-500 hover:text-gray-700'
                      }`}
                      onClick={() => setInstallMethod('script')}
                    >
                      {t('scriptCommand')}
                    </button>
                  </div>

                  {/* 自然语言面板 */}
                  {installMethod === 'nl' && (
                    <div>
                      <div className="relative bg-indigo-50/40 border border-dashed border-indigo-200/60 rounded-lg pl-4 pr-9 py-3">
                        <div className="text-sm text-gray-700">
                          {t('nlImportCommand', { name: cliInfo.resourceName })}
                        </div>
                        <button
                          className="absolute top-2.5 right-2.5 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all"
                          onClick={() => {
                            const text = t('nlImportCommand', { name: cliInfo.resourceName });
                            copyToClipboard(text).then(() => {
                              setCopiedNl(true);
                              setTimeout(() => setCopiedNl(false), 2000);
                            });
                          }}
                        >
                          {copiedNl ? (
                            <CheckOutlined className="text-green-500" />
                          ) : (
                            <CopyOutlined />
                          )}
                        </button>
                      </div>
                      <div className="text-xs text-gray-500 mt-2 ml-1">{t('nlInstallHint')}</div>
                    </div>
                  )}

                  {/* 脚本命令面板 */}
                  {installMethod === 'script' && (
                    <div>
                      <div className="flex gap-2 mb-2">
                        <button
                          className={`text-xs px-2.5 py-1 rounded transition-colors ${
                            hiclawPlatform === 'unix'
                              ? 'bg-blue-500 text-white'
                              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                          }`}
                          onClick={() => setHiclawPlatform('unix')}
                        >
                          Linux / Mac
                        </button>
                        <button
                          className={`text-xs px-2.5 py-1 rounded transition-colors ${
                            hiclawPlatform === 'windows'
                              ? 'bg-blue-500 text-white'
                              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                          }`}
                          onClick={() => setHiclawPlatform('windows')}
                        >
                          Windows
                        </button>
                      </div>
                      <div className="relative rounded-md bg-gray-50/80 border border-gray-200 border-l-[2.5px] border-l-indigo-300/60 pl-3 pr-9 py-2.5">
                        <button
                          className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all"
                          onClick={() => {
                            const version = selectedVersion || 'v1';
                            const encodedName = encodeURIComponent(cliInfo.resourceName);
                            const hostPart = cliInfo.nacosPort
                              ? `${cliInfo.nacosHost}:${cliInfo.nacosPort}`
                              : cliInfo.nacosHost;
                            const selectedVersionInfo = versions.find((v) => v.version === version);
                            const isLatest = selectedVersionInfo?.isLatest ?? false;
                            const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                            const canOmitPackage = isDefaultHost && isLatest;
                            const versionPath = isLatest ? '' : `/${version}`;
                            const packageUrl = `nacos://${hostPart}/${cliInfo.namespace}/${encodedName}${versionPath}`;
                            const packageArg = canOmitPackage ? '' : ` --package "${packageUrl}"`;
                            const cmd =
                              hiclawPlatform === 'unix'
                                ? `curl -fsSL https://higress.ai/hiclaw/import.sh | bash -s -- worker --name "${cliInfo.resourceName}"${packageArg}`
                                : `irm https://higress.ai/hiclaw/import.ps1 -OutFile import.ps1; .\\import.ps1 worker --name "${cliInfo.resourceName}"${packageArg}`;
                            copyToClipboard(cmd).then(() => {
                              setCopiedHiclaw(true);
                              setTimeout(() => setCopiedHiclaw(false), 2002);
                            });
                          }}
                        >
                          {copiedHiclaw ? (
                            <CheckOutlined className="text-green-500" />
                          ) : (
                            <CopyOutlined />
                          )}
                        </button>
                        <code
                          className="text-[12px] text-gray-700 break-all"
                          style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                        >
                          {(() => {
                            const version = selectedVersion || 'v1';
                            const encodedName = encodeURIComponent(cliInfo.resourceName);
                            const hostPart = cliInfo.nacosPort
                              ? `${cliInfo.nacosHost}:${cliInfo.nacosPort}`
                              : cliInfo.nacosHost;
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
                <div className="px-4 py-3" style={{ borderBottom: '1px solid #edeef3' }}>
                  <div className="flex items-center gap-1.5 mb-2">
                    <CloudUploadOutlined className="text-indigo-400/80 text-[13px]" />
                    <span className="text-xs font-semibold text-gray-600 tracking-wide">
                      {t('httpDownload')}
                    </span>
                  </div>
                  <div className="relative rounded-md bg-gray-50/80 border border-gray-200 border-l-[2.5px] border-l-indigo-300/60 pl-3 pr-9 py-2.5">
                    <button
                      className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all disabled:opacity-50"
                      disabled={!selectedVersion}
                      onClick={() => {
                        const selectedVersionInfo = versions.find(
                          (v) => v.version === selectedVersion,
                        );
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionParam =
                          selectedVersion && !isLatest
                            ? `?version=${encodeURIComponent(selectedVersion)}`
                            : '';
                        const url = `${window.location.origin}/api/v1/workers/${workerProductId}/download${versionParam}`;
                        copyToClipboard(url).then(() => {
                          setCopiedHttp(true);
                          setTimeout(() => setCopiedHttp(false), 2000);
                        });
                      }}
                    >
                      {copiedHttp ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                    </button>
                    <code
                      className="text-[12px] text-gray-700 break-all"
                      style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                    >
                      {(() => {
                        const selectedVersionInfo = versions.find(
                          (v) => v.version === selectedVersion,
                        );
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionParam =
                          selectedVersion && !isLatest
                            ? `?version=${encodeURIComponent(selectedVersion)}`
                            : '';
                        return `${typeof window !== 'undefined' ? window.location.origin : ''}/api/v1/workers/${workerProductId}/download${versionParam}`;
                      })()}
                    </code>
                  </div>
                </div>
              )}

              {/* Nacos CLI command */}
              {cliInfo && (
                <div className="px-4 py-3">
                  <div className="flex items-center gap-1.5 mb-2">
                    <CodeOutlined className="text-indigo-400/80 text-[13px]" />
                    <span className="text-xs font-semibold text-gray-600 tracking-wide">
                      {t('npxDownload')}
                    </span>
                  </div>
                  <div className="relative rounded-md bg-gray-50/80 border border-gray-200 border-l-[2.5px] border-l-indigo-300/60 pl-3 pr-9 py-2.5">
                    <button
                      className="absolute top-2 right-2 p-1 rounded text-gray-400 hover:text-indigo-500 hover:bg-indigo-50 transition-all"
                      onClick={() => {
                        const quotedName = cliInfo.resourceName.includes(' ')
                          ? `"${cliInfo.resourceName}"`
                          : cliInfo.resourceName;
                        const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                        const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                        const selectedVersionInfo = versions.find(
                          (v) => v.version === selectedVersion,
                        );
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                        const cmd = `npx @nacos-group/cli${hostArg} agentspec-get ${quotedName}${versionArg}`;
                        copyToClipboard(cmd).then(() => {
                          setCopiedCmd(true);
                          setTimeout(() => setCopiedCmd(false), 2000);
                        });
                      }}
                    >
                      {copiedCmd ? <CheckOutlined className="text-green-500" /> : <CopyOutlined />}
                    </button>
                    <code
                      className="text-[12px] text-gray-700 break-all"
                      style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                    >
                      {(() => {
                        const isDefaultHost = cliInfo.nacosHost === 'market.hiclaw.io';
                        const hostArg = isDefaultHost ? '' : ` --host ${cliInfo.nacosHost}`;
                        const selectedVersionInfo = versions.find(
                          (v) => v.version === selectedVersion,
                        );
                        const isLatest = selectedVersionInfo?.isLatest ?? false;
                        const versionArg = isLatest ? '' : ` --version ${selectedVersion}`;
                        return `npx @nacos-group/cli${hostArg} agentspec-get ${cliInfo.resourceName.includes(' ') ? `"${cliInfo.resourceName}"` : cliInfo.resourceName}${versionArg}`;
                      })()}
                    </code>
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
