import {
  ArrowLeftOutlined,
  DownloadOutlined,
  CopyOutlined,
  CheckOutlined,
  FileFilled,
  FileTextOutlined,
  FolderOpenOutlined,
  CodeOutlined,
  EyeOutlined,
  CloudUploadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Alert, Tag, Button, Select, Tooltip } from 'antd';
import hljs from 'highlight.js';
import { useEffect, useState, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useParams, useNavigate } from 'react-router-dom';

import { ProductIconRenderer } from '../components/icon/ProductIconRenderer';
import { Layout } from '../components/Layout';
import 'highlight.js/styles/github.css';
import { SkillWorkerDetailSkeleton } from '../components/loading';
import { ProductDetailTabLabel, ProductDetailTabs } from '../components/ProductDetailTabs';
import { ProductOverview } from '../components/ProductOverview';
import SkillFileTree from '../components/skill/SkillFileTree';
import APIs from '../lib/apis';
import {
  getSkillFiles,
  getSkillFileContent,
  getSkillPackageUrl,
  getSkillVersions,
  getSkillCliInfo,
} from '../lib/apis/cliProvider';
import { getIconString } from '../lib/iconUtils';
import { buildNacosCliCommand } from '../lib/nacosCliCommand';
import { copyToClipboard } from '../lib/utils';
import { formatSkillAuthor, getSelectedSkillVersionAuthor } from '../lib/utils/skillVersionInfo';

import type { IProductDetail } from '../lib/apis';
import type {
  SkillFileTreeNode,
  SkillFileContent,
  SkillVersion,
  SkillCliInfo,
} from '../lib/apis/cliProvider';
import type { ISkillConfig } from '../lib/apis/typing';

type IdeType =
  | 'qoder'
  | 'qoderwork'
  | 'claude'
  | 'codex'
  | 'cursor'
  | 'kiro'
  | 'lingma'
  | 'qwenpaw'
  | 'openclaw';

const IDE_OPTIONS: { value: IdeType; label: string; icon: string }[] = [
  { icon: '/qwenpaw-symbol.svg', label: 'QwenPaw', value: 'qwenpaw' },
  { icon: '/openclaw.svg', label: 'OpenClaw', value: 'openclaw' },
  { icon: 'https://g.alicdn.com/qbase/qoder/0.0.65/favIcon.svg', label: 'Qoder', value: 'qoder' },
  {
    icon: 'https://img.alicdn.com/imgextra/i1/O1CN01clv0Oy1Tia1VN1WEO_!!6000000002416-1-tps-1200-1200.gif',
    label: 'QoderWork',
    value: 'qoderwork',
  },
  {
    icon: 'https://img.alicdn.com/imgextra/i3/O1CN01JqyNKC1VmMU2MHdF9_!!6000000002695-55-tps-100-101.svg',
    label: 'Claude',
    value: 'claude',
  },
  {
    icon: 'https://img.alicdn.com/imgextra/i3/O1CN011DvgjK1s54F8K000Q_!!6000000005714-0-tps-248-248.jpg',
    label: 'Codex',
    value: 'codex',
  },
  {
    icon: 'https://aimg.alistatic.com/i/dm885X/UgW1Qzx0RPwrNIVs4sWlr-7b5511235d.svg',
    label: 'Cursor',
    value: 'cursor',
  },
  { icon: '/kiro.png', label: 'Kiro', value: 'kiro' },
  {
    icon: 'https://img.alicdn.com/imgextra/i2/O1CN01OR7j0c1OvKuJfKBAw_!!6000000001767-2-tps-280-280.png',
    label: 'Lingma',
    value: 'lingma',
  },
];

const getDefaultOutputDir = (ide: IdeType): string => {
  const dirMap: Record<IdeType, string> = {
    claude: '~/.claude/skills',
    codex: '~/.codex/skills',
    cursor: '~/.cursor/skills',
    kiro: '~/.kiro/skills',
    lingma: '~/.lingma/skills',
    openclaw: '~/.openclaw/skills',
    qoder: '~/.qoder/skills',
    qoderwork: '~/.qoderwork/skills',
    qwenpaw: '~/.qwenpaw/skill_pool',
  };
  return dirMap[ide];
};

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

function SkillDetail() {
  const { skillProductId } = useParams<{ skillProductId: string }>();
  const navigate = useNavigate();
  const { i18n, t } = useTranslation('skillDetail');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
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
  const [copiedProfile, setCopiedProfile] = useState(false);
  const [mdRawMode, setMdRawMode] = useState(true);
  const [versions, setVersions] = useState<SkillVersion[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<string | undefined>();
  const [cliInfo, setCliInfo] = useState<SkillCliInfo | null>(null);
  const [selectedIde, setSelectedIde] = useState<IdeType>('qwenpaw');
  const [outputDir, setOutputDir] = useState<string>('~/.qwenpaw/skill_pool');

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
      if (!skillProductId) return;
      try {
        const filesRes = await getSkillFiles(skillProductId, version).catch(() => null);
        if (
          filesRes?.code === 'SUCCESS' &&
          Array.isArray(filesRes.data) &&
          filesRes.data.length > 0
        ) {
          const nodes = filesRes.data;
          setFileTree(nodes);
          const hasSkillMd = nodes.some((n: SkillFileTreeNode) => n.path === 'SKILL.md');
          if (hasSkillMd) {
            setSelectedFilePath('SKILL.md');
            setFileLoading(true);
            setOverviewLoading(true);
            getSkillFileContent(skillProductId, 'SKILL.md', version)
              .then((r) => {
                if (r.code === 'SUCCESS' && r.data) {
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
    },
    [skillProductId],
  );

  useEffect(() => {
    const fetchDetail = async () => {
      if (!skillProductId) return;
      setLoading(true);
      setError('');
      try {
        const [productRes, versionsRes, cliInfoRes] = await Promise.all([
          APIs.getProduct({ id: skillProductId }),
          getSkillVersions(skillProductId).catch(() => null),
          getSkillCliInfo(skillProductId).catch(() => null),
        ]);
        if (productRes.code === 'SUCCESS' && productRes.data) {
          setData(productRes.data);
          if (productRes.data.skillConfig) {
            setSkillConfig(productRes.data.skillConfig);
          }
        } else {
          setError(productRes.message || t('dataLoadFailed'));
        }

        // Set CLI download info
        if (cliInfoRes?.code === 'SUCCESS' && cliInfoRes.data) {
          setCliInfo(cliInfoRes.data);
        }

        // Only show online (published) versions in frontend
        const allVersions =
          versionsRes?.code === 'SUCCESS' && Array.isArray(versionsRes.data)
            ? versionsRes.data
            : [];
        const onlineVersions = allVersions.filter((v: SkillVersion) => v.status === 'online');
        setVersions(onlineVersions);

        // Prefer the backend-labeled latest version; otherwise keep the existing list order.
        const defaultVersion =
          onlineVersions.find((version: SkillVersion) => version.isLatest)?.version ??
          onlineVersions[0]?.version;
        setSelectedVersion(defaultVersion);

        // Load file tree for the default version
        await loadVersionContent(defaultVersion);

        // Fallback: if no online versions but product has document, use it as overview
        if (onlineVersions.length === 0 && allVersions.length > 0 && productRes.data?.document) {
          setOverviewContent(productRes.data.document);
        }
      } catch (err) {
        console.error('API请求失败:', err);
        setError(t('loadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [skillProductId, loadVersionContent, t]);

  const handleVersionChange = useCallback(
    async (version: string) => {
      setSelectedVersion(version);
      setFileContent(null);
      setSelectedFilePath(undefined);
      await loadVersionContent(version);
    },
    [loadVersionContent],
  );

  const handleSelectFile = useCallback(
    async (path: string) => {
      if (!skillProductId) return;
      setSelectedFilePath(path);
      setMdRawMode(true);
      setFileLoading(true);
      try {
        const res = await getSkillFileContent(skillProductId, path, selectedVersion);
        if (res.code === 'SUCCESS' && res.data) {
          setFileContent(res.data);
        }
      } catch {
        setFileContent(null);
      } finally {
        setFileLoading(false);
      }
    },
    [skillProductId, selectedVersion],
  );

  const handleDownload = useCallback(() => {
    if (!skillProductId) return;
    const a = document.createElement('a');
    a.href = getSkillPackageUrl(skillProductId, selectedVersion);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }, [skillProductId, selectedVersion]);

  const handleTabChange = useCallback((key: string) => {
    if (key === 'overview' || key === 'file') {
      setActiveTab(key);
    }
  }, []);

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
            description={error || t('skillNotExist')}
            message={t('error')}
            showIcon
            type="error"
          />
        </div>
      </Layout>
    );
  }

  const { description, name } = data;
  const hasFiles = fileTree.length > 0;
  const isAiRegistrySkill = skillConfig?.registryType === 'AIREGISTRY';
  const installResourceName = cliInfo?.resourceName || skillConfig?.skillName || name;
  const showNpxInstall = Boolean(cliInfo) || isAiRegistrySkill;
  const showHttpDownload = versions.length > 0;
  const selectedVersionInfo = versions.find((v) => v.version === selectedVersion);
  const latestVersion = versions.find((v) => v.isLatest)?.version;
  const downloadCount = Math.max(
    skillConfig?.downloadCount ?? 0,
    selectedVersionInfo?.downloadCount ?? 0,
  );
  const selectedAuthorLabel = formatSkillAuthor(
    getSelectedSkillVersionAuthor(versions, selectedVersion),
  );
  const formattedUpdatedAt = data.updatedAt
    ? new Date(data.updatedAt)
        .toLocaleDateString(i18n.language, {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
        })
        .replace(/\//g, '.')
    : undefined;
  const updatedAtLabel = data.updatedAt
    ? t('updatedAt', {
        date: formattedUpdatedAt,
      })
    : undefined;
  const headerMetaItems = [
    updatedAtLabel,
    selectedAuthorLabel ? `${t('author')} ${selectedAuthorLabel}` : undefined,
  ].filter(Boolean);
  const selectedInstallVersion =
    selectedVersion && !selectedVersionInfo?.isLatest ? selectedVersion : undefined;
  const skillGetCommand = buildNacosCliCommand({
    command: 'skill-get',
    outputDir,
    resourceName: installResourceName,
    server: cliInfo || undefined,
    version: selectedInstallVersion,
  });
  const httpDownloadUrl = `${
    typeof window !== 'undefined' ? window.location.origin : ''
  }/api/v1/skills/${skillProductId}/download${
    selectedVersion ? `?version=${encodeURIComponent(selectedVersion)}` : ''
  }`;

  const renderFilePreview = () => {
    if (!selectedFilePath) {
      return (
        <div className="flex h-full items-center justify-center bg-[#FBFCFE] text-gray-400">
          <div className="text-center">
            <FileFilled className="mb-3 text-5xl text-gray-300" />
            <p className="text-sm text-gray-400">{t('clickFileToView')}</p>
          </div>
        </div>
      );
    }
    if (fileLoading) {
      return (
        <div className="flex justify-center py-16">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-gray-200 border-t-colorPrimary" />
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
        <div className="relative flex h-full flex-1 flex-col overflow-auto bg-white">
          {/* Toggle button - floats top-right */}
          <div className="absolute right-3 top-2 z-20">
            <Tooltip title={mdRawMode ? t('renderPreview') : t('sourceCode')}>
              <button
                className="flex items-center gap-1 rounded-[7px] border border-[#E8EDF5] bg-white/90 px-2 py-1 text-xs font-medium text-gray-500 shadow-sm transition-colors hover:bg-[#F7F9FC] hover:text-gray-700"
                onClick={() => setMdRawMode(!mdRawMode)}
                type="button"
              >
                {mdRawMode ? <EyeOutlined /> : <CodeOutlined />}
                <span>{mdRawMode ? t('previewMode') : t('sourceMode')}</span>
              </button>
            </Tooltip>
          </div>
          {mdRawMode ? (
            <div className="flex flex-1 overflow-auto">
              <div
                className="sticky left-0 z-10 flex-shrink-0 select-none bg-white py-3 pl-4 pr-3 text-right"
                style={{
                  borderRight: '1px solid #E8EEF6',
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
                className="m-0 flex-1 bg-white py-3 pl-5 pr-4"
                style={{ fontFamily: codeFont, fontSize: '13px', lineHeight: '20px' }}
              >
                <code
                  className="hljs language-markdown"
                  dangerouslySetInnerHTML={{ __html: highlighted }}
                />
              </pre>
            </div>
          ) : (
            <ProductOverview
              className="flex-1 px-6 pb-6 pt-10"
              content={fileContent.content}
              emptyText={t('noContent')}
              showFrontmatterTable
            />
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
      <div className="h-full flex-1 overflow-auto bg-white">
        <div className="flex min-h-full">
          <div
            className="sticky left-0 z-10 flex-shrink-0 select-none bg-white py-3 pl-4 pr-3 text-right"
            style={{
              borderRight: '1px solid #E8EEF6',
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
            className="m-0 flex-1 bg-white py-3 pl-5 pr-4"
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
      <div className="mx-auto flex w-full max-w-[1480px] flex-col gap-5 py-5 sm:py-7">
        {/* Page header */}
        <div className="flex-shrink-0">
          <button
            className="mb-4 inline-flex h-9 items-center gap-2 rounded-[10px] px-3 text-sm font-medium text-gray-600 transition-all duration-200 hover:bg-white/80 hover:text-gray-950 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-colorPrimary/30 active:translate-y-px"
            onClick={() => navigate(-1)}
            type="button"
          >
            <ArrowLeftOutlined className="text-xs" />
            <span>{t('back')}</span>
          </button>

          <div className="rounded-[14px] border border-[#DDE5F0] bg-white/90 p-5 shadow-[0_18px_50px_rgba(15,23,42,0.06)] backdrop-blur-sm">
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                <div className="flex min-w-0 items-start gap-4">
                  {data.icon && data.icon.value ? (
                    <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center overflow-hidden rounded-[12px] border border-[#E1E7F0] bg-[#F7F9FC]">
                      <ProductIconRenderer
                        className="h-full w-full object-cover"
                        iconType={getIconString(data.icon)}
                      />
                    </div>
                  ) : (
                    <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-[12px] border border-[#E1E7F0] bg-[#F7F9FC] text-colorPrimary">
                      <ThunderboltOutlined className="text-2xl" />
                    </div>
                  )}

                  <div className="min-w-0 flex-1">
                    <h1 className="text-2xl font-semibold leading-tight text-gray-950">{name}</h1>

                    <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-500">
                      {headerMetaItems.map((item, index) => (
                        <span className="min-w-0 truncate" key={`${item}-${index}`}>
                          {item}
                        </span>
                      ))}
                      <span className="inline-flex flex-shrink-0 items-center gap-1.5">
                        <DownloadOutlined className="text-xs text-gray-400" />
                        <span className="tabular-nums">{downloadCount.toLocaleString()}</span>
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {description && (
                <p className="m-0 max-w-5xl break-words text-sm leading-6 text-gray-600">
                  {description}
                </p>
              )}
            </div>
          </div>
        </div>

        {/* Main content */}
        <div className="flex flex-col gap-5 xl:flex-row">
          {/* Left: file viewer with Overview / File tabs */}
          <div className="min-w-0 flex-1">
            <ProductDetailTabs
              activeKey={activeTab}
              cardClassName="flex flex-col"
              items={[
                {
                  children: (
                    <ProductOverview
                      className="h-full min-h-[420px]"
                      content={overviewContent}
                      emptyText={t('noSkillMd')}
                      loading={overviewLoading}
                      showFrontmatterTable
                    />
                  ),
                  key: 'overview',
                  label: (
                    <ProductDetailTabLabel icon={<FileTextOutlined />}>
                      {t('overviewTab')}
                    </ProductDetailTabLabel>
                  ),
                },
                {
                  children: (
                    <div className="flex h-full min-h-0 overflow-hidden rounded-[10px] border border-[#E8EEF6]">
                      {/* File tree */}
                      <div
                        className="scrollbar-thin-soft flex-shrink-0 overflow-y-auto overflow-x-hidden border-r border-[#E8EEF6] bg-[#FBFCFE] p-2"
                        style={{ width: treeWidth }}
                      >
                        {hasFiles ? (
                          <SkillFileTree
                            nodes={fileTree}
                            onSelect={handleSelectFile}
                            selectedPath={selectedFilePath}
                          />
                        ) : (
                          <div className="flex h-full items-center justify-center text-sm text-gray-400">
                            {t('noFiles')}
                          </div>
                        )}
                      </div>
                      {/* Drag handle */}
                      <div
                        aria-orientation="vertical"
                        className="w-1 flex-shrink-0 cursor-col-resize bg-transparent transition-colors hover:bg-colorPrimary/20"
                        onMouseDown={handleDragStart}
                        role="separator"
                      />
                      {/* File preview */}
                      <div className="flex min-w-0 flex-1 flex-col overflow-auto">
                        {renderFilePreview()}
                      </div>
                    </div>
                  ),
                  key: 'file',
                  label: (
                    <ProductDetailTabLabel icon={<FolderOpenOutlined />}>
                      {t('fileTab')}
                    </ProductDetailTabLabel>
                  ),
                },
              ]}
              onChange={handleTabChange}
              style={{ height: 'calc(100vh - 280px)', minHeight: 520 }}
              tabsClassName="flex min-h-0 flex-1 flex-col [&_.ant-tabs-content-holder]:min-h-0 [&_.ant-tabs-content-holder]:flex-1 [&_.ant-tabs-content-holder]:overflow-hidden [&_.ant-tabs-content]:h-full [&_.ant-tabs-tabpane]:h-full"
            />
          </div>

          {/* Right sidebar: download card */}
          <div className="order-1 w-full flex-shrink-0 xl:order-2 xl:sticky xl:top-24 xl:w-[390px] xl:self-start">
            <div className="overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-white/90 shadow-[0_18px_50px_rgba(15,23,42,0.05)] backdrop-blur-sm">
              <div className="border-b border-[#E8EEF6] bg-[#FBFCFE] p-3">
                <div className="mb-1.5 text-xs font-semibold text-gray-500">{t('version')}</div>
                <div className="flex items-center gap-2">
                  <Select
                    className="h-8 min-w-0 flex-1 [&_.ant-select-selection-item]:!leading-8 [&_.ant-select-selection-placeholder]:!leading-8 [&_.ant-select-selection-search-input]:!h-8 [&_.ant-select-selector]:!h-8 [&_.ant-select-selector]:!rounded-[9px] [&_.ant-select-selector]:!border-[#DDE5F0]"
                    disabled={versions.length === 0}
                    onChange={handleVersionChange}
                    options={versions.map((v) => ({
                      label: (
                        <div className="flex items-center gap-1.5">
                          <span>{v.version}</span>
                          {v.version === latestVersion && (
                            <Tag className="!m-0 !text-xs !px-1.5 !py-0 !leading-5" color="blue">
                              latest
                            </Tag>
                          )}
                        </div>
                      ),
                      value: v.version,
                    }))}
                    placeholder={t('noVersion')}
                    size="middle"
                    value={selectedVersion}
                  />
                  <Tooltip color="#111827" title={t('downloadSkillPackage')}>
                    <Button
                      aria-label={t('downloadSkillPackage')}
                      className="!h-8 !rounded-[9px] !border-[#DDE5F0] !px-2.5 !text-xs !font-medium !text-gray-600 hover:!border-colorPrimary/40 hover:!text-colorPrimary"
                      disabled={versions.length === 0}
                      icon={<DownloadOutlined />}
                      onClick={handleDownload}
                    >
                      {t('downloadPackage')}
                    </Button>
                  </Tooltip>
                </div>
              </div>

              {showNpxInstall && (
                <>
                  <div className="border-b border-[#E8EEF6] px-4 py-3">
                    <div className="mb-3 flex items-center gap-1.5">
                      <CodeOutlined className="text-[13px] text-gray-400" />
                      <span className="text-xs font-semibold text-gray-600">
                        {t('npxDownload')}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-3">
                      {IDE_OPTIONS.map((ide) => (
                        <button
                          className={`flex h-8 min-w-0 items-center gap-1.5 rounded-[8px] border px-2 text-xs font-medium transition-all ${
                            selectedIde === ide.value
                              ? 'border-colorPrimary bg-colorPrimaryBg text-colorPrimary shadow-[0_4px_12px_rgba(99,102,241,0.12)]'
                              : 'border-[#E3E9F3] bg-white text-gray-600 hover:border-[#C6D1E3] hover:bg-[#FAFBFF] hover:text-gray-900'
                          }`}
                          key={ide.value}
                          onClick={() => {
                            setSelectedIde(ide.value);
                            setOutputDir(getDefaultOutputDir(ide.value));
                          }}
                          type="button"
                        >
                          {ide.icon && (
                            <img
                              alt={ide.label}
                              className="h-4 w-4 flex-shrink-0 object-contain"
                              src={ide.icon}
                            />
                          )}
                          <span className="truncate">{ide.label}</span>
                        </button>
                      ))}
                    </div>

                    <div className="mt-3">
                      <div className="mb-1.5 text-xs font-semibold text-gray-600">
                        {t('outputDir')}
                      </div>
                      <input
                        className="w-full rounded-[10px] border border-[#DDE5F0] bg-white px-3 py-2 text-xs text-gray-700 transition-colors focus:border-colorPrimary focus:outline-none focus:ring-2 focus:ring-colorPrimary/15"
                        onChange={(e) => setOutputDir(e.target.value)}
                        placeholder={t('outputDirPlaceholder')}
                        type="text"
                        value={outputDir}
                      />
                    </div>

                    <div className="mt-3 space-y-3">
                      {isAiRegistrySkill && (
                        <div>
                          <div className="mb-1.5 text-xs font-semibold text-gray-700">
                            {t('cliCredentialStep')}
                            <a
                              className="ml-1 text-colorPrimary hover:text-colorPrimaryHover"
                              href="https://help.aliyun.com/zh/mse/user-guide/nacos-cli-access-ai-registry-login-credential-configuration-guide?spm=5176.mse-prod.console-base_help.dexternal.66a72675UrvmaY"
                              rel="noreferrer"
                              target="_blank"
                            >
                              {t('cliUserDoc')}
                            </a>
                          </div>
                          <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827] py-2.5 pl-3 pr-9">
                            <Button
                              aria-label={t('copyCommand')}
                              className="absolute right-2 top-2 z-10 !h-6 !w-6 !min-w-6 !p-0 !text-gray-400 hover:!text-white [&_.anticon]:!text-xs"
                              icon={
                                copiedProfile ? (
                                  <CheckOutlined className="text-green-400" />
                                ) : (
                                  <CopyOutlined />
                                )
                              }
                              onClick={() => {
                                copyToClipboard('npx @nacos-group/cli profile edit').then(() => {
                                  setCopiedProfile(true);
                                  setTimeout(() => setCopiedProfile(false), 2000);
                                });
                              }}
                              size="small"
                              title={t('copyCommand')}
                              type="text"
                            />
                            <code
                              className="break-all text-[12px] leading-5 text-gray-100"
                              style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                            >
                              npx @nacos-group/cli profile edit
                            </code>
                          </div>
                        </div>
                      )}

                      <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827] py-2.5 pl-3 pr-9">
                        <Button
                          aria-label={t('copyCommand')}
                          className="absolute right-2 top-2 z-10 !h-6 !w-6 !min-w-6 !p-0 !text-gray-400 hover:!text-white [&_.anticon]:!text-xs"
                          icon={
                            copied ? <CheckOutlined className="text-green-400" /> : <CopyOutlined />
                          }
                          onClick={() => {
                            copyToClipboard(skillGetCommand).then(() => {
                              setCopied(true);
                              setTimeout(() => setCopied(false), 2000);
                            });
                          }}
                          size="small"
                          title={t('copyCommand')}
                          type="text"
                        />
                        <code
                          className="break-all text-[12px] leading-5 text-gray-100"
                          style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                        >
                          {skillGetCommand}
                        </code>
                      </div>
                    </div>
                  </div>
                </>
              )}

              {/* HTTP download */}
              {showHttpDownload && (
                <div className="px-4 py-3">
                  <div className="mb-2 flex items-center gap-1.5">
                    <CloudUploadOutlined className="text-[13px] text-gray-400" />
                    <span className="text-xs font-semibold text-gray-600">{t('httpDownload')}</span>
                  </div>
                  <div className="relative overflow-hidden rounded-[12px] border border-[#172033] bg-[#111827] py-2.5 pl-3 pr-9">
                    <Button
                      aria-label={t('copyDownloadUrl')}
                      className="absolute right-2 top-2 z-10 !h-6 !w-6 !min-w-6 !p-0 !text-gray-400 hover:!text-white [&_.anticon]:!text-xs"
                      disabled={!selectedVersion}
                      icon={
                        copiedHttp ? <CheckOutlined className="text-green-400" /> : <CopyOutlined />
                      }
                      onClick={() => {
                        copyToClipboard(httpDownloadUrl).then(() => {
                          setCopiedHttp(true);
                          setTimeout(() => setCopiedHttp(false), 2000);
                        });
                      }}
                      size="small"
                      title={t('copyDownloadUrl')}
                      type="text"
                    />
                    <code
                      className="break-all text-[12px] leading-5 text-gray-100"
                      style={{ fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace" }}
                    >
                      {httpDownloadUrl}
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

export default SkillDetail;
