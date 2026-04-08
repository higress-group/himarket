import {
  UploadOutlined,
  FolderFilled,
  FolderOpenFilled,
  FileFilled,
  FileMarkdownFilled,
  FileTextFilled,
  CodeFilled,
  SettingFilled,
  Html5Filled,
  FileZipFilled,
  FileImageFilled,
  JavaScriptOutlined,
  JavaOutlined,
  PythonOutlined,
  DockerOutlined,
  RightOutlined,
  DownOutlined,
  ExclamationCircleFilled,
  CheckCircleFilled,
  CloseCircleFilled,
  LinkOutlined,
} from '@ant-design/icons';
import { Upload, message, Spin, Tooltip, Button, Select, Tag, Modal, Space, Form } from 'antd';
import hljs from 'highlight.js';
import { useState, useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeHighlight from 'rehype-highlight';
import remarkGfm from 'remark-gfm';

import { apiProductApi, nacosApi } from '@/lib/api';
import { workerApi } from '@/lib/api';
import 'github-markdown-css/github-markdown-light.css';
import 'highlight.js/styles/github.css';
import type { ApiProduct } from '@/types/api-product';

import type { UploadRequestOption } from 'rc-upload/lib/interface';

// ── Parse YAML front matter from markdown (supports | and > multiline) ──
function parseFrontMatter(content: string): { frontmatter: Record<string, string>; body: string } {
  const trimmed = content.trim();
  if (!trimmed.startsWith('---')) return { body: trimmed, frontmatter: {} };
  const secondDash = trimmed.indexOf('---', 3);
  if (secondDash === -1) return { body: trimmed, frontmatter: {} };
  const yamlBlock = trimmed.substring(3, secondDash).trim();
  const body = trimmed.substring(secondDash + 3).trim();
  const frontmatter: Record<string, string> = {};
  const lines = yamlBlock.split('\n');
  let currentKey = '';
  let currentValue = '';
  let multilineIndent = -1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line === undefined) continue;
    if (multilineIndent >= 0) {
      const stripped = line.replace(/^\s*/, '');
      const indent = line.length - stripped.length;
      if (indent > multilineIndent || stripped === '') {
        currentValue += (currentValue ? '\n' : '') + stripped;
        continue;
      }
      frontmatter[currentKey] = currentValue.trim();
      multilineIndent = -1;
    }
    const colonIdx = line.indexOf(':');
    if (
      colonIdx > 0 &&
      line.substring(0, colonIdx).trim() === line.substring(0, colonIdx).trimStart()
    ) {
      const key = line.substring(0, colonIdx).trim();
      let value = line.substring(colonIdx + 1).trim();
      if (value === '|' || value === '>' || value === '|-' || value === '>-') {
        currentKey = key;
        currentValue = '';
        multilineIndent = line.length - line.trimStart().length;
        continue;
      }
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      frontmatter[key] = value;
    }
  }
  if (multilineIndent >= 0 && currentKey) {
    frontmatter[currentKey] = currentValue.trim();
  }
  return { body, frontmatter };
}

interface WorkerFileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  encoding?: string;
  size?: number;
  children?: WorkerFileTreeNode[];
}

interface FileContent {
  path: string;
  content: string;
  encoding: string;
  size: number;
}

interface VersionItem {
  version: string;
  updateTime?: number;
  status?: string; // draft, reviewing, online, offline
  downloadCount?: number;
  publishPipelineInfo?: string;
  isLatest?: boolean;
}

interface PipelineNode {
  nodeId?: string;
  durationMs?: number;
  message?: string;
  executedAt?: string;
  passed?: boolean;
}

interface ApiProductWorkerPackageProps {
  apiProduct: ApiProduct;
  onUploadSuccess?: () => void;
  handleRefresh: () => void;
}

// ── File icon by extension (using Ant Design icons) ───────────────
const iconClass = 'flex-shrink-0';
const iconStyle = { fontSize: 14 };

function FileIcon({ name }: { name: string }) {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  const lowerName = name.toLowerCase();

  // Special file names
  if (lowerName === 'dockerfile')
    return <DockerOutlined className={iconClass} style={{ ...iconStyle, color: '#1a9ad0' }} />;
  if (lowerName === '.gitignore')
    return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;
  if (lowerName === 'license' || lowerName === 'notice')
    return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;

  switch (ext) {
    case 'md':
      return (
        <FileMarkdownFilled className={iconClass} style={{ ...iconStyle, color: '#1a72bd' }} />
      );
    case 'json':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#7568b8' }} />;
    case 'yaml':
    case 'yml':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#c88a0a' }} />;
    case 'toml':
      return <SettingFilled className={iconClass} style={{ ...iconStyle, color: '#c88a0a' }} />;
    case 'xml':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#cc5e1e' }} />;
    case 'html':
      return <Html5Filled className={iconClass} style={{ ...iconStyle, color: '#d94020' }} />;
    case 'css':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#2060b0' }} />;
    case 'js':
    case 'jsx':
      return (
        <JavaScriptOutlined className={iconClass} style={{ ...iconStyle, color: '#c89008' }} />
      );
    case 'ts':
    case 'tsx':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#1e68b0' }} />;
    case 'py':
      return <PythonOutlined className={iconClass} style={{ ...iconStyle, color: '#2060a0' }} />;
    case 'java':
      return <JavaOutlined className={iconClass} style={{ ...iconStyle, color: '#cc5818' }} />;
    case 'sh':
    case 'bash':
      return <CodeFilled className={iconClass} style={{ ...iconStyle, color: '#208848' }} />;
    case 'zip':
    case 'tar':
    case 'gz':
      return <FileZipFilled className={iconClass} style={{ ...iconStyle, color: '#b88520' }} />;
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'svg':
      return <FileImageFilled className={iconClass} style={{ ...iconStyle, color: '#5848b0' }} />;
    case 'txt':
    case 'log':
    case 'csv':
      return <FileTextFilled className={iconClass} style={{ ...iconStyle, color: '#999' }} />;
    default:
      return <FileFilled className={iconClass} style={{ ...iconStyle, color: '#3880c0' }} />;
  }
}

// ── 文件树组件 ─────────────────
interface TreeNodeProps {
  node: WorkerFileTreeNode;
  selectedPath?: string;
  onSelect: (path: string) => void;
  depth: number;
}

function TreeNode({ depth, node, onSelect, selectedPath }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const isDir = node.type === 'directory';
  const isSelected = node.path === selectedPath;

  return (
    <div>
      <Tooltip mouseEnterDelay={0.8} placement="right" title={node.name}>
        <div
          className={`
            flex items-center gap-1 px-1 py-[2px] rounded cursor-pointer text-[13px] select-none
            transition-colors duration-100
            ${isSelected ? 'bg-blue-100 text-gray-900' : 'hover:bg-gray-100 text-gray-700'}
          `}
          onClick={() => (isDir ? setExpanded((v) => !v) : onSelect(node.path))}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              if (isDir) {
                setExpanded((v) => !v);
              } else {
                onSelect(node.path);
              }
            }
          }}
          role="button"
          style={{ paddingLeft: `${4 + depth * 16}px` }}
          tabIndex={0}
        >
          {isDir ? (
            <span className="w-4 flex items-center justify-center flex-shrink-0 text-[10px] text-gray-400">
              {expanded ? <DownOutlined /> : <RightOutlined />}
            </span>
          ) : (
            <span className="w-4 flex-shrink-0" />
          )}
          {isDir ? (
            expanded ? (
              <FolderOpenFilled className="text-amber-500 flex-shrink-0 text-sm" />
            ) : (
              <FolderFilled className="text-amber-400 flex-shrink-0 text-sm" />
            )
          ) : (
            <FileIcon name={node.name} />
          )}
          <span className="truncate ml-0.5">{node.name}</span>
        </div>
      </Tooltip>
      {isDir && expanded && node.children && node.children.length > 0 && (
        <div>
          {node.children.map((child) => (
            <TreeNode
              depth={depth + 1}
              key={child.path}
              node={child}
              onSelect={onSelect}
              selectedPath={selectedPath}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function WorkerFileTree({
  nodes,
  onSelect,
  selectedPath,
}: {
  nodes: WorkerFileTreeNode[];
  selectedPath?: string;
  onSelect: (p: string) => void;
}) {
  return (
    <div className="py-1">
      {nodes.map((node) => (
        <TreeNode
          depth={0}
          key={node.path}
          node={node}
          onSelect={onSelect}
          selectedPath={selectedPath}
        />
      ))}
    </div>
  );
}

function findNode(nodes: WorkerFileTreeNode[], path: string): WorkerFileTreeNode | null {
  for (const node of nodes) {
    if (node.path === path) return node;
    if (node.children) {
      const f = findNode(node.children, path);
      if (f) return f;
    }
  }
  return null;
}

export function ApiProductWorkerPackage({
  apiProduct,
  handleRefresh,
  onUploadSuccess,
}: ApiProductWorkerPackageProps) {
  const productId = apiProduct.productId;
  const workerConfig = apiProduct.workerConfig;
  const hasNacos = !!workerConfig?.nacosId;
  const [fileTree, setFileTree] = useState<WorkerFileTreeNode[]>([]);
  const [selectedPath, setSelectedPath] = useState<string | undefined>();
  const [selectedFile, setSelectedFile] = useState<FileContent | null>(null);
  const [loadingTree, setLoadingTree] = useState(false);
  const [loadingFile, setLoadingFile] = useState(false);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [versions, setVersions] = useState<VersionItem[]>([]);
  const [previewVersion, setPreviewVersion] = useState<string | undefined>(
    workerConfig?.currentVersion,
  );
  const [treeWidth, setTreeWidth] = useState(240);
  const isDragging = useRef(false);
  const [activeTab, setActiveTab] = useState<'overview' | 'file'>('overview');
  const [overviewContent, setOverviewContent] = useState<string | null>(null);
  const [loadingOverview, setLoadingOverview] = useState(false);

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

  const fetchFileTree = async (version?: string) => {
    setLoadingTree(true);
    try {
      const res = (await workerApi.getFiles(productId, version)) as { data?: WorkerFileTreeNode[] };
      const nodes: WorkerFileTreeNode[] = res.data || [];
      setFileTree(nodes);
      if (findNode(nodes, 'manifest.json')) loadFileContent('manifest.json', version);
      // Fetch AGENTS.md: check root first, then config/ subdirectory
      const agentsMdNode = findNode(nodes, 'AGENTS.md') || findNode(nodes, 'config/AGENTS.md');
      if (agentsMdNode) {
        fetchOverview(agentsMdNode.path, version);
      } else {
        setOverviewContent(null);
      }
    } catch {
    } finally {
      setLoadingTree(false);
    }
  };

  const fetchOverview = async (path: string, version?: string) => {
    setLoadingOverview(true);
    try {
      const res = (await workerApi.getFileContent(productId, path, version)) as {
        data?: { content?: string };
      };
      setOverviewContent(res.data?.content ?? null);
    } catch {
      setOverviewContent(null);
    } finally {
      setLoadingOverview(false);
    }
  };

  const loadFileContent = async (path: string, version?: string) => {
    setSelectedPath(path);
    setLoadingFile(true);
    try {
      const res = (await workerApi.getFileContent(productId, path, version)) as {
        data?: FileContent;
      };
      setSelectedFile(res.data || null);
    } catch {
    } finally {
      setLoadingFile(false);
    }
  };

  const fetchVersions = async (silent = false) => {
    if (!silent) setLoadingVersions(true);
    try {
      const res = (await workerApi.getVersions(productId)) as { data?: VersionItem[] };
      const versionItems: VersionItem[] = res.data || [];
      setVersions(versionItems);
      setPreviewVersion((prev) => {
        if (prev && versionItems.some((item) => item.version === prev)) {
          return prev;
        }
        return versionItems[0]?.version;
      });
    } catch {
      // ignore
    } finally {
      if (!silent) setLoadingVersions(false);
    }
  };

  const previewItem = versions.find((item) => item.version === previewVersion);
  const latestVersion = versions.find((item) => item.isLatest)?.version;

  // Parse publishPipelineInfo from version data
  const pipelineStatus = (() => {
    const raw = previewItem?.publishPipelineInfo;
    if (!raw) return null;
    try {
      return typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch {
      return null;
    }
  })();

  const isUnpublished = previewItem?.status === 'draft' || previewItem?.status === 'offline';
  const isOnline = previewItem?.status === 'online';
  const isReviewing = previewItem?.status === 'reviewing';
  const canPublish = !!previewVersion && isUnpublished;
  const canOffline = !!previewVersion && isOnline;
  const canDeleteDraft = !!previewVersion && isUnpublished;
  const showPublishActions = canPublish || isReviewing;
  const totalDownloads = versions.reduce((sum, item) => sum + (item.downloadCount ?? 0), 0);

  const [actionLoading, setActionLoading] = useState<string | null>(null);

  // Nacos modal state
  const [nacosModalVisible, setNacosModalVisible] = useState(false);
  const [nacosInstances, setNacosInstances] = useState<
    Array<{ nacosId: string; nacosName: string; isDefault?: boolean }>
  >([]);
  const [nacosNsOptions, setNacosNsOptions] = useState<
    Array<{ namespaceId: string; namespaceName?: string }>
  >([]);
  const [nacosLoading, setNacosLoading] = useState(false);
  const [nsLoading, setNsLoading] = useState(false);
  const [nacosSaving, setNacosSaving] = useState(false);
  const [nacosForm] = Form.useForm();
  const [currentNacosName, setCurrentNacosName] = useState<string>('');

  // Fetch nacos name
  useEffect(() => {
    if (apiProduct.workerConfig?.nacosId) {
      nacosApi
        .getNacos({ page: 1, size: 1000 })
        .then((res) => {
          const list =
            (res as { data?: { content?: Array<{ nacosId: string; nacosName: string }> } }).data
              ?.content || [];
          const found = list.find((n) => n.nacosId === apiProduct.workerConfig?.nacosId);
          setCurrentNacosName(found?.nacosName || apiProduct.workerConfig?.nacosId || '');
        })
        .catch(() => {});
    }
  }, [apiProduct.workerConfig?.nacosId]);

  const fetchNacosInstances = async () => {
    setNacosLoading(true);
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 1000 });
      setNacosInstances(res.data?.content || []);
    } finally {
      setNacosLoading(false);
    }
  };

  const handleNacosChange = async (nacosId: string) => {
    nacosForm.setFieldValue('namespace', undefined);
    setNacosNsOptions([]);
    setNsLoading(true);
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
      setNacosNsOptions(res.data?.content || []);
    } finally {
      setNsLoading(false);
    }
  };

  const openNacosModal = () => {
    fetchNacosInstances();
    const currentNacosId = apiProduct.workerConfig?.nacosId;
    const currentNamespace = apiProduct.workerConfig?.namespace || 'public';
    if (currentNacosId) {
      nacosForm.setFieldsValue({ nacosId: currentNacosId, namespace: currentNamespace });
      handleNacosChange(currentNacosId);
    }
    setNacosModalVisible(true);
  };

  const handleNacosSave = async () => {
    const values = await nacosForm.validateFields();
    setNacosSaving(true);
    try {
      await apiProductApi.updateWorkerNacos(apiProduct.productId, {
        nacosId: values.nacosId,
        namespace: values.namespace,
      });
      message.success('Nacos 关联已更新');
      setNacosModalVisible(false);
      handleRefresh();
    } finally {
      setNacosSaving(false);
    }
  };

  const handlePublishVersion = async (version: string) => {
    setActionLoading('publish');
    try {
      await workerApi.publishVersion(productId, version);
      message.success(`版本 ${version} 发布成功`);
      setPreviewVersion(version);
      await Promise.all([fetchVersions(), fetchFileTree(version)]);
      onUploadSuccess?.();
    } catch (error: unknown) {
      const errMsg = error instanceof Error ? error.message : '版本发布失败';
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleOfflineVersion = async (version: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要将版本 ${version} 下线吗？下线后该版本将无法被运行时查询。`,
      icon: <ExclamationCircleFilled />,
      okText: '确认下线',
      okType: 'danger',
      onOk: async () => {
        setActionLoading('offline');
        try {
          await workerApi.offlineVersion(productId, version);
          message.success(`版本 ${version} 已下线`);
          await fetchVersions();
          onUploadSuccess?.();
        } catch (error: unknown) {
          const errMsg = error instanceof Error ? error.message : '下线失败';
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: '确认下线',
    });
  };

  const handleSetLatest = async (version: string) => {
    setActionLoading('setLatest');
    try {
      await workerApi.setLatestVersion(productId, version);
      message.success(`版本 ${version} 已设为 latest`);
      await fetchVersions();
    } catch (error: unknown) {
      const errMsg = error instanceof Error ? error.message : '设置 latest 失败';
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteDraft = async () => {
    Modal.confirm({
      cancelText: '取消',
      content: '删除草稿后不可恢复，已发布的版本不受影响。',
      icon: <ExclamationCircleFilled />,
      okText: '确认删除',
      okType: 'danger',
      onOk: async () => {
        setActionLoading('deleteDraft');
        try {
          await workerApi.deleteDraft(productId);
          message.success('草稿已删除');
          const nextVersion = versions.find((v) => v.status === 'online')?.version;
          setPreviewVersion(nextVersion);
          await fetchVersions();
          if (nextVersion) {
            await fetchFileTree(nextVersion);
          } else {
            setFileTree([]);
            setSelectedFile(null);
            setSelectedPath(undefined);
            setOverviewContent(null);
          }
          onUploadSuccess?.();
        } catch (error: unknown) {
          const errMsg = error instanceof Error ? error.message : '删除草稿失败';
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: '确认删除草稿',
    });
  };

  useEffect(() => {
    fetchFileTree(previewVersion);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId, previewVersion]);

  useEffect(() => {
    fetchVersions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId]);

  // Auto-poll version list when any version is in reviewing state
  const hasReviewing = versions.some((item) => item.status === 'reviewing');
  useEffect(() => {
    if (!hasReviewing) return;
    const timer = setInterval(() => fetchVersions(true), 5000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasReviewing, productId]);

  const customRequest = async (options: UploadRequestOption) => {
    const { file, onError, onSuccess } = options;
    setUploading(true);
    try {
      // file 可能是 UploadRequestFile 类型 (string | RcFile)，需要转换为 File
      const fileToUpload =
        typeof file === 'string' ? new File([], file) : (file as unknown as File);
      const res = (await workerApi.uploadPackage(productId, fileToUpload)) as { data?: unknown };
      message.success('上传成功');
      onSuccess?.(res);
      const versionsRes = (await workerApi.getVersions(productId)) as { data?: VersionItem[] };
      const versionItems: VersionItem[] = versionsRes.data || [];
      setVersions(versionItems);
      const firstVersion = versionItems[0]?.version;
      setPreviewVersion(firstVersion);
      await fetchFileTree(firstVersion);
      onUploadSuccess?.();
    } catch (error: unknown) {
      message.destroy();
      const errMsg = error instanceof Error ? error.message : '上传失败';
      message.error(errMsg);
      const errObj = error instanceof Error ? error : new Error(String(error));
      onError?.(errObj);
    } finally {
      setUploading(false);
    }
  };

  const renderPreview = () => {
    if (loadingFile)
      return (
        <div className="flex items-center justify-center h-full">
          <Spin />
        </div>
      );

    if (!selectedFile)
      return (
        <div className="flex items-center justify-center h-full text-gray-400">
          <div className="text-center">
            <FileFilled className="text-4xl mb-2 text-gray-300" />
            <p>点击左侧文件查看内容</p>
          </div>
        </div>
      );

    if (selectedFile.encoding === 'base64')
      return (
        <div className="flex items-center justify-center h-full text-gray-400">
          <p>二进制文件，不支持预览</p>
        </div>
      );

    if (selectedFile.path.endsWith('.md')) {
      const highlighted = (() => {
        try {
          if (hljs.getLanguage('markdown')) {
            return hljs.highlight(selectedFile.content, { language: 'markdown' }).value;
          }
          return hljs.highlightAuto(selectedFile.content).value;
        } catch {
          return selectedFile.content
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        }
      })();
      const lineCount = selectedFile.content.split('\n').length;
      const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";
      return (
        <div className="flex-1 overflow-auto bg-white h-full">
          <div className="flex min-h-full">
            <div
              className="select-none text-right pr-3 pt-4 pb-4 pl-3 text-xs text-gray-400 bg-[#f6f8fa] border-r border-[#d0d7de] flex-shrink-0"
              style={{ fontFamily: codeFont, lineHeight: '1.6', minWidth: 48 }}
            >
              {Array.from({ length: lineCount }, (_, i) => (
                <div key={i + 1}>{i + 1}</div>
              ))}
            </div>
            <pre
              className="flex-1 m-0 pt-4 pb-4 pl-4 pr-4 text-xs overflow-x-auto"
              style={{ background: 'transparent', fontFamily: codeFont, lineHeight: '1.6' }}
            >
              <code
                className="hljs language-markdown"
                dangerouslySetInnerHTML={{ __html: highlighted }}
              />
            </pre>
          </div>
        </div>
      );
    }

    const lang = (() => {
      const fileName = selectedFile.path.split('/').pop()?.toLowerCase() ?? '';
      if (fileName === 'dockerfile') return 'dockerfile';
      const ext = selectedFile.path.split('.').pop()?.toLowerCase() ?? '';
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
      return map[ext] || 'plaintext';
    })();

    const highlighted = (() => {
      try {
        if (lang && lang !== 'plaintext' && hljs.getLanguage(lang)) {
          return hljs.highlight(selectedFile.content, { language: lang }).value;
        }
        return hljs.highlightAuto(selectedFile.content).value;
      } catch {
        return selectedFile.content
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;');
      }
    })();

    const lineCount = selectedFile.content.split('\n').length;
    const codeFont = "'Menlo', 'Monaco', 'Courier New', monospace";

    return (
      <div className="flex-1 overflow-auto bg-white h-full">
        <div className="flex min-h-full">
          <div
            className="flex-shrink-0 py-3 pr-3 pl-4 text-right select-none border-r border-gray-100 sticky left-0 bg-white z-10"
            style={{ fontFamily: codeFont, fontSize: '13px', lineHeight: '20px' }}
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
    <div className="p-6 space-y-4 h-full flex flex-col">
      <div>
        <h1 className="text-2xl font-bold mb-1">Worker Package</h1>
        <p className="text-gray-600">上传并管理 Worker 包文件</p>
      </div>

      {/* Card 1: Version Management */}
      <div className="border rounded-lg bg-white p-4 space-y-3">
        {/* Nacos status row */}
        <div className="flex items-center justify-between gap-4 pb-3 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Tag
              style={{
                background: '#f5f5f5',
                borderColor: '#d9d9d9',
                fontSize: 14,
                margin: 0,
                padding: '4px 12px',
              }}
            >
              {currentNacosName} / {apiProduct.workerConfig?.namespace || 'public'}
            </Tag>
          </div>
          <Button
            icon={<LinkOutlined />}
            onClick={openNacosModal}
            style={{ background: '#6B5CE7', borderColor: '#6B5CE7' }}
            type="primary"
          >
            {apiProduct.workerConfig?.nacosId ? '切换Nacos' : '关联Nacos'}
          </Button>
        </div>

        {/* Row 1: Version selector + action buttons + upload */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3 flex-wrap">
            <Space.Compact size="large">
              <Select
                className="w-48"
                onChange={(value) => setPreviewVersion(value)}
                options={versions.map((item) => ({
                  label: (
                    <div className="flex items-center gap-2">
                      <span>{item.version}</span>
                      {item.version === latestVersion && (
                        <Tag className="!m-0 !text-xs" color="blue">
                          latest
                        </Tag>
                      )}
                    </div>
                  ),
                  value: item.version,
                }))}
                placeholder="版本"
                value={previewVersion}
              />
              <Button
                className="!bg-gray-100 !text-gray-700 !border-gray-300 hover:!bg-gray-200 disabled:!bg-gray-50 disabled:!text-gray-400"
                disabled={!previewVersion || !isOnline || previewVersion === latestVersion}
                loading={actionLoading === 'setLatest'}
                onClick={() => previewVersion && handleSetLatest(previewVersion)}
              >
                设为 Latest
              </Button>
            </Space.Compact>
            {loadingVersions && <Spin size="small" />}
            {showPublishActions && (
              <Button
                disabled={isReviewing}
                loading={actionLoading === 'publish'}
                onClick={() => previewVersion && handlePublishVersion(previewVersion)}
                type="primary"
              >
                提交审核
              </Button>
            )}
            {(canDeleteDraft || isReviewing) && (
              <Button
                danger
                disabled={isReviewing}
                loading={actionLoading === 'deleteDraft'}
                onClick={handleDeleteDraft}
              >
                删除草稿
              </Button>
            )}
            {canOffline && (
              <Button
                danger
                loading={actionLoading === 'offline'}
                onClick={() => previewVersion && handleOfflineVersion(previewVersion)}
              >
                版本下线
              </Button>
            )}
          </div>

          <Upload
            accept=".zip"
            capture={undefined}
            customRequest={customRequest}
            disabled={uploading || !hasNacos}
            hasControlInside={false}
            pastable={false}
            showUploadList={false}
          >
            <Button
              className="!h-auto !px-4 !py-2.5"
              disabled={!hasNacos}
              icon={<UploadOutlined />}
              loading={uploading}
              style={
                !hasNacos ? { background: '#f5f5f5', borderColor: '#d9d9d9', color: '#bfbfbf' } : {}
              }
            >
              <div className="leading-snug text-left">
                <div className="text-sm">上传 Worker 包</div>
                <div className="text-xs text-gray-400">.zip, 最大 10MB</div>
              </div>
            </Button>
          </Upload>
        </div>

        {/* Row 2: Status + downloads + pipeline result */}
        <div className="flex items-center gap-3 text-sm" style={{ minHeight: 32 }}>
          <Tag
            bordered={false}
            className={`!m-0 ${
              previewItem?.status === 'online'
                ? '!bg-green-50 !text-green-600'
                : previewItem?.status === 'reviewing'
                  ? '!bg-blue-50 !text-blue-600'
                  : '!bg-gray-100 !text-gray-500'
            }`}
          >
            {previewItem?.status === 'online'
              ? '已上线'
              : previewItem?.status === 'reviewing'
                ? '审核中'
                : '未发布'}
          </Tag>
          <span className="text-gray-400">
            下载 <strong className="text-gray-600">{totalDownloads}</strong>
          </span>
          {(() => {
            const pStatus = pipelineStatus?.status;
            const isApproved = pStatus === 'APPROVED' || previewItem?.status === 'online';
            const isRejected = pStatus === 'REJECTED';
            const isInProgress = isReviewing && !isApproved && !isRejected;
            const pipelineNodes = pipelineStatus?.pipeline as PipelineNode[] | undefined;

            if (isInProgress) {
              return (
                <span className="inline-flex items-center gap-1.5 text-xs text-blue-600">
                  <Spin size="small" />
                  <span className="font-medium">审核中</span>
                  {pipelineNodes && pipelineNodes.length > 0 && (
                    <span className="text-blue-400">
                      ({pipelineNodes.filter((n) => n.passed).length}/{pipelineNodes.length})
                    </span>
                  )}
                </span>
              );
            }
            if (isApproved && (isOnline || previewItem?.publishPipelineInfo)) {
              return (
                <span className="inline-flex items-center gap-1 text-xs text-green-600">
                  <CheckCircleFilled />
                  <span className="font-medium">审核通过</span>
                </span>
              );
            }
            if (isRejected) {
              return (
                <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-red-50 border border-red-100 rounded-lg text-xs text-red-500 ml-6">
                  <CloseCircleFilled />
                  <span className="font-medium">审核未通过</span>
                  <Button
                    className="!p-0 !text-red-500 !text-xs"
                    onClick={() =>
                      Modal.info({
                        content: (
                          <div className="mt-2 space-y-3">
                            {pipelineNodes
                              ?.filter((n) => !n.passed)
                              .map((node, idx) => (
                                <div className="p-4 bg-gray-50 rounded-lg" key={idx}>
                                  <div className="flex items-start gap-3">
                                    <CloseCircleFilled className="text-red-500 mt-0.5 text-base flex-shrink-0" />
                                    <div className="flex-1 min-w-0">
                                      <div className="flex items-center gap-2 mb-1">
                                        <span className="font-semibold text-gray-800">
                                          {node.nodeId}
                                        </span>
                                        {node.durationMs !== null &&
                                          node.durationMs !== undefined && (
                                            <span className="text-xs text-gray-400">
                                              {(node.durationMs / 1000).toFixed(1)}s
                                            </span>
                                          )}
                                      </div>
                                      {node.message && (
                                        <div className="text-sm text-gray-500 whitespace-pre-wrap break-words">
                                          {node.message}
                                        </div>
                                      )}
                                      {node.executedAt && (
                                        <div className="flex items-center gap-1 mt-2 text-xs text-gray-400">
                                          <span>⏱</span>
                                          <span>
                                            {new Date(node.executedAt).toLocaleString('zh-CN')}
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                </div>
                              ))}
                          </div>
                        ),
                        icon: null,
                        title: '审核未通过',
                        width: 600,
                      })
                    }
                    size="small"
                    type="link"
                  >
                    详情
                  </Button>
                </span>
              );
            }
            return null;
          })()}
        </div>
      </div>

      {/* Card 2: Overview / File Preview */}
      <div
        className="border rounded-lg overflow-hidden bg-white flex-1 flex flex-col"
        style={{ minHeight: 600 }}
      >
        {/* Tab header */}
        <div className="flex gap-6 px-4 pt-3 border-b">
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

        {/* Tab content */}
        {activeTab === 'overview' ? (
          <div className="flex-1 overflow-auto p-6" style={{ height: 560 }}>
            {loadingOverview ? (
              <div className="flex justify-center pt-8">
                <Spin size="small" />
              </div>
            ) : overviewContent ? (
              (() => {
                const { body, frontmatter } = parseFrontMatter(overviewContent);
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
                    <ReactMarkdown rehypePlugins={[rehypeHighlight]} remarkPlugins={[remarkGfm]}>
                      {body}
                    </ReactMarkdown>
                  </div>
                );
              })()
            ) : (
              <div className="text-gray-400 text-sm text-center pt-8">
                该版本未包含 AGENTS.md 文件
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-1 min-h-0" style={{ height: 560 }}>
            <div
              className="border-r bg-white overflow-y-auto overflow-x-hidden flex-shrink-0 p-2"
              style={{ width: treeWidth }}
            >
              {loadingTree ? (
                <div className="flex items-center justify-center h-full">
                  <Spin size="small" />
                </div>
              ) : fileTree.length === 0 ? (
                <div className="flex items-center justify-center h-full text-gray-400 text-sm">
                  暂无文件
                </div>
              ) : (
                <WorkerFileTree
                  nodes={fileTree}
                  onSelect={(path) => loadFileContent(path, previewVersion)}
                  selectedPath={selectedPath}
                />
              )}
            </div>
            {/* 拖拽分隔条 */}
            <div
              className="w-1 flex-shrink-0 cursor-col-resize hover:bg-blue-200 transition-colors bg-transparent"
              onMouseDown={handleDragStart}
              role="slider"
              tabIndex={0}
            />
            <div className="flex-1 overflow-auto flex flex-col" style={{ height: 560 }}>
              {renderPreview()}
            </div>
          </div>
        )}
      </div>

      {/* Nacos Modal */}
      <Modal
        cancelText="取消"
        confirmLoading={nacosSaving}
        okText="确认"
        onCancel={() => setNacosModalVisible(false)}
        onOk={handleNacosSave}
        open={nacosModalVisible}
        title="关联 Nacos 实例"
      >
        <Form form={nacosForm} layout="vertical">
          <Form.Item
            label="Nacos 实例"
            name="nacosId"
            rules={[{ message: '请选择 Nacos 实例', required: true }]}
          >
            <Select
              loading={nacosLoading}
              onChange={handleNacosChange}
              options={nacosInstances.map((n) => ({
                label: `${n.nacosName}${n.isDefault ? ' (默认)' : ''}`,
                value: n.nacosId,
              }))}
              placeholder="选择 Nacos 实例"
            />
          </Form.Item>
          <Form.Item
            label="命名空间"
            name="namespace"
            rules={[{ message: '请选择命名空间', required: true }]}
          >
            <Select
              loading={nsLoading}
              options={nacosNsOptions.map((ns) => ({
                label: ns.namespaceName || ns.namespaceId || 'public',
                value: ns.namespaceId || '',
              }))}
              placeholder="选择命名空间"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
