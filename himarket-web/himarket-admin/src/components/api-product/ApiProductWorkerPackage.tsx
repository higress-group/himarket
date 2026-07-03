import {
  UploadOutlined,
  FileFilled,
  ExclamationCircleFilled,
  CheckCircleFilled,
  CloseCircleFilled,
  LinkOutlined,
  EditOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import {
  Button,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Upload,
  type UploadProps,
} from 'antd';
import hljs from 'highlight.js';
import { useState, useEffect, useRef } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, nacosApi } from '@/lib/api';
import { workerApi } from '@/lib/api';
import type { AgentSpecCard, ApiProduct, WorkerDraft } from '@/types/api-product';

import {
  cloneAgentSpecCard,
  findAgentSpecResourceKey,
  getAgentSpecCardFileContent,
} from './package-management/agentSpecCardFiles';
import { PackageContentPanel } from './package-management/PackageContentPanel';
import { findPackageFileNode, getEditorLanguage } from './package-management/packageFileUtils';

import type {
  PackageFileContent,
  PackageFileTreeNode,
  PackagePipelineNode,
  PackageVersionItem,
} from './package-management/types';

import 'highlight.js/styles/github.css';

type WorkerFileTreeNode = PackageFileTreeNode;
type FileContent = PackageFileContent;
type VersionItem = PackageVersionItem;
type PipelineNode = PackagePipelineNode;

interface ApiProductWorkerPackageProps {
  apiProduct: ApiProduct;
  onUploadSuccess?: () => void;
  handleRefresh: () => void;
}

export function ApiProductWorkerPackage({
  apiProduct,
  handleRefresh,
  onUploadSuccess,
}: ApiProductWorkerPackageProps) {
  const productId = apiProduct.productId;
  const { locale, t } = useLocale();
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
  const [authorModalVisible, setAuthorModalVisible] = useState(false);
  const [versionAuthor, setVersionAuthor] = useState('');
  const [treeWidth, setTreeWidth] = useState(240);
  const isDragging = useRef(false);
  const lastAutoFileTreeKeyRef = useRef('');
  const lastAutoVersionsProductIdRef = useRef('');
  const [activeTab, setActiveTab] = useState<'overview' | 'file'>('overview');
  const [overviewContent, setOverviewContent] = useState<string | null>(null);
  const [overviewPath, setOverviewPath] = useState<string | undefined>();
  const [loadingOverview, setLoadingOverview] = useState(false);
  const [draftAgentSpecCard, setDraftAgentSpecCard] = useState<AgentSpecCard | null>(null);
  const [draftWorkingCard, setDraftWorkingCard] = useState<AgentSpecCard | null>(null);
  const [draftEditedPaths, setDraftEditedPaths] = useState<string[]>([]);
  const [draftEditContent, setDraftEditContent] = useState('');
  const [draftDirty, setDraftDirty] = useState(false);
  const [draftFileDirty, setDraftFileDirty] = useState(false);
  const [draftEditing, setDraftEditing] = useState(false);
  const [loadingDraft, setLoadingDraft] = useState(false);
  const draftSeqRef = useRef(0);

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

  const rememberDraftEditedPath = (path: string) => {
    setDraftEditedPaths((paths) => (paths.includes(path) ? paths : [...paths, path]));
  };

  const buildDraftCardWithCurrentFile = (baseAgentSpecCard: AgentSpecCard) => {
    if (!selectedFile) {
      return null;
    }

    const nextAgentSpecCard = cloneAgentSpecCard(baseAgentSpecCard);
    if (selectedFile.path === 'manifest.json') {
      try {
        const manifest = JSON.parse(draftEditContent) as {
          worker?: { suggested_name?: string };
        };
        const suggestedName = manifest.worker?.suggested_name?.trim();
        const currentName = draftAgentSpecCard?.name || baseAgentSpecCard.name;
        if (suggestedName && currentName && suggestedName !== currentName) {
          message.warning(t('product.package.draftNameReadonly'));
          return null;
        }
      } catch {
        message.warning(t('product.package.invalidJson'));
        return null;
      }
      nextAgentSpecCard.content = draftEditContent;
      return nextAgentSpecCard;
    }

    const resourceKey = findAgentSpecResourceKey(nextAgentSpecCard, selectedFile.path);
    if (!resourceKey || !nextAgentSpecCard.resource?.[resourceKey]) {
      return null;
    }

    nextAgentSpecCard.resource = {
      ...nextAgentSpecCard.resource,
      [resourceKey]: {
        ...nextAgentSpecCard.resource[resourceKey],
        content: draftEditContent,
      },
    };
    return nextAgentSpecCard;
  };

  const commitCurrentDraftFile = () => {
    if (!draftEditing || !draftFileDirty) {
      return draftWorkingCard;
    }
    if (!draftWorkingCard || !selectedFile) {
      return null;
    }

    const nextAgentSpecCard = buildDraftCardWithCurrentFile(draftWorkingCard);
    if (!nextAgentSpecCard) {
      return null;
    }

    setDraftWorkingCard(nextAgentSpecCard);
    rememberDraftEditedPath(selectedFile.path);
    setDraftFileDirty(false);
    return nextAgentSpecCard;
  };

  const fetchFileTree = async (version?: string) => {
    lastAutoFileTreeKeyRef.current = `${productId}-${version ?? ''}`;
    setLoadingTree(true);
    setDraftEditContent('');
    setDraftDirty(false);
    setDraftFileDirty(false);
    setDraftEditing(false);
    setDraftWorkingCard(null);
    setDraftEditedPaths([]);
    setOverviewPath(undefined);
    try {
      const res = (await workerApi.getFiles(productId, version)) as { data?: WorkerFileTreeNode[] };
      const nodes: WorkerFileTreeNode[] = res.data || [];
      setFileTree(nodes);
      if (findPackageFileNode(nodes, 'manifest.json')) loadFileContent('manifest.json', version);
      // Fetch AGENTS.md: check root first, then config/ subdirectory
      const agentsMdNode =
        findPackageFileNode(nodes, 'AGENTS.md') || findPackageFileNode(nodes, 'config/AGENTS.md');
      if (agentsMdNode) {
        setOverviewPath(agentsMdNode.path);
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
    const agentSpecCardWithCurrentEdit = commitCurrentDraftFile();
    if (draftEditing && draftFileDirty && !agentSpecCardWithCurrentEdit) {
      return;
    }

    setSelectedPath(path);
    setLoadingFile(true);
    try {
      const res = (await workerApi.getFileContent(productId, path, version)) as {
        data?: FileContent;
      };
      const nextFile = res.data || null;
      const hasEditedContent =
        draftEditedPaths.includes(path) ||
        (draftEditing && draftFileDirty && selectedFile?.path === path);
      const editedContent =
        draftEditing && agentSpecCardWithCurrentEdit && hasEditedContent
          ? getAgentSpecCardFileContent(agentSpecCardWithCurrentEdit, path)
          : undefined;
      setSelectedFile(nextFile);
      setDraftEditContent(editedContent ?? nextFile?.content ?? '');
      setDraftFileDirty(false);
      if (!draftEditing) {
        setDraftDirty(false);
        setDraftWorkingCard(null);
        setDraftEditedPaths([]);
      }
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

  const isDraft = previewItem?.status === 'draft';
  const isOffline = previewItem?.status === 'offline';
  const isOnline = previewItem?.status === 'online';
  const isReviewing = previewItem?.status === 'reviewing';
  const isApproved = previewItem?.status === 'approved';
  const hasDraftOrReviewing = versions.some(
    (item) => item.status === 'draft' || item.status === 'reviewing',
  );
  const canPublish = !!previewVersion && isDraft;
  const canPublishApproved = !!previewVersion && isApproved;
  const canOnline = !!previewVersion && isOffline;
  const canOffline = !!previewVersion && isOnline;
  const canDeleteDraft = !!previewVersion && isDraft;
  const showCreateDraft = !!previewVersion;
  const canCreateDraft =
    showCreateDraft && (isOnline || isOffline) && !hasDraftOrReviewing && hasNacos;
  const showEditDraft = isDraft;
  const hasEditableDraftFile = selectedFile
    ? selectedFile.encoding !== 'base64'
    : !!overviewContent;
  const canEditDraft =
    showEditDraft && hasEditableDraftFile && !!draftAgentSpecCard && !loadingDraft && !draftEditing;
  const createDraftDisabledTip = !hasNacos
    ? t('product.package.createDraftRegistryRequired')
    : !(isOnline || isOffline)
      ? t('product.package.createDraftBaseStatusTip')
      : hasDraftOrReviewing
        ? t('product.package.createDraftDisabledTip')
        : undefined;
  const editDraftDisabledTip =
    !selectedFile && !overviewContent
      ? t('product.package.editDraftFileRequired')
      : selectedFile?.encoding === 'base64'
        ? t('product.package.editDraftBinaryUnsupported')
        : undefined;
  const showPublishActions = canPublish || isReviewing;
  const totalDownloads = versions.reduce((sum, item) => sum + (item.downloadCount ?? 0), 0);
  const infoChipClass =
    'inline-flex h-7 items-center gap-1.5 rounded-md border px-2.5 text-xs leading-none';
  const neutralInfoChipClass = `${infoChipClass} border-gray-100 bg-gray-50 text-gray-500`;
  const statusInfoChipClass = `${infoChipClass} ${
    previewItem?.status === 'online'
      ? 'border-green-100 bg-green-50 text-green-600'
      : previewItem?.status === 'reviewing'
        ? 'border-blue-100 bg-blue-50 text-blue-600'
        : previewItem?.status === 'approved'
          ? 'border-amber-100 bg-amber-50 text-amber-600'
          : 'border-gray-100 bg-gray-50 text-gray-500'
  }`;
  const statusText =
    previewItem?.status === 'online'
      ? t('product.package.statusOnline')
      : previewItem?.status === 'reviewing'
        ? t('product.package.statusReviewing')
        : previewItem?.status === 'approved'
          ? t('product.package.statusApproved')
          : t('product.package.statusUnpublished');

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
  const lastNacosNameFetchKeyRef = useRef('');

  // Fetch nacos name
  useEffect(() => {
    const nacosId = apiProduct.workerConfig?.nacosId;
    if (!nacosId) {
      lastNacosNameFetchKeyRef.current = '';
      setCurrentNacosName('');
      return;
    }

    if (lastNacosNameFetchKeyRef.current === nacosId) {
      return;
    }
    lastNacosNameFetchKeyRef.current = nacosId;

    nacosApi
      .getNacos({ page: 1, size: 1000 })
      .then((res) => {
        if (lastNacosNameFetchKeyRef.current !== nacosId) {
          return;
        }

        const list =
          (res as { data?: { content?: Array<{ nacosId: string; nacosName: string }> } }).data
            ?.content || [];
        const found = list.find((n) => n.nacosId === nacosId);
        setCurrentNacosName(found?.nacosName || nacosId);
      })
      .catch(() => {});
  }, [apiProduct.workerConfig?.nacosId]);

  useEffect(() => {
    const seq = ++draftSeqRef.current;
    if (!isDraft || !previewVersion) {
      setDraftAgentSpecCard(null);
      setDraftWorkingCard(null);
      setDraftEditedPaths([]);
      setDraftEditing(false);
      setDraftDirty(false);
      setDraftFileDirty(false);
      setLoadingDraft(false);
      return;
    }

    setLoadingDraft(true);
    workerApi
      .getDraft(productId)
      .then((res: unknown) => {
        if (seq !== draftSeqRef.current) return;
        const draft = (res as { data?: WorkerDraft }).data;
        setDraftAgentSpecCard(draft?.agentSpecCard ?? null);
      })
      .catch(() => {
        if (seq !== draftSeqRef.current) return;
        setDraftAgentSpecCard(null);
        setDraftWorkingCard(null);
        setDraftEditedPaths([]);
        setDraftDirty(false);
        setDraftFileDirty(false);
        setDraftEditing(false);
      })
      .finally(() => {
        if (seq === draftSeqRef.current) {
          setLoadingDraft(false);
        }
      });
  }, [isDraft, previewVersion, productId]);

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
      await apiProductApi.updateProductSource(apiProduct.productId, {
        nacosId: values.nacosId,
        namespace: values.namespace,
        registryType: 'NACOS',
        sourceType: 'NACOS',
      });
      message.success(t('product.package.nacosUpdated'));
      setNacosModalVisible(false);
      handleRefresh();
    } finally {
      setNacosSaving(false);
    }
  };

  const confirmDiscardDraftChanges = (next: () => void) => {
    if (!draftDirty) {
      next();
      return;
    }
    Modal.confirm({
      cancelText: t('product.package.cancelDraftDiscard'),
      content: t('product.package.discardDraftChangesConfirm'),
      okText: t('product.package.discardDraftChangesOk'),
      okType: 'danger',
      onOk: next,
      title: t('product.package.discardDraftChangesTitle'),
    });
  };

  const startDraftEdit = () => {
    const overviewFile =
      overviewContent && overviewPath
        ? {
            content: overviewContent,
            encoding: 'text',
            path: overviewPath,
            size: new Blob([overviewContent]).size,
          }
        : null;
    const fileToEdit = activeTab === 'overview' ? overviewFile : selectedFile || overviewFile;

    if (!fileToEdit || !draftAgentSpecCard) {
      return;
    }
    setActiveTab('file');
    setSelectedFile(fileToEdit);
    setSelectedPath(fileToEdit.path);
    setDraftWorkingCard(cloneAgentSpecCard(draftAgentSpecCard));
    setDraftEditedPaths([]);
    setDraftEditContent(fileToEdit.content);
    setDraftDirty(false);
    setDraftFileDirty(false);
    setDraftEditing(true);
  };

  const cancelDraftEdit = () => {
    confirmDiscardDraftChanges(() => {
      setDraftEditContent(selectedFile?.content ?? '');
      setDraftDirty(false);
      setDraftFileDirty(false);
      setDraftEditing(false);
      setDraftWorkingCard(null);
      setDraftEditedPaths([]);
    });
  };

  const switchContentTab = (tab: 'overview' | 'file') => {
    if (tab === activeTab) {
      return;
    }
    if (!draftEditing) {
      setActiveTab(tab);
      return;
    }
    confirmDiscardDraftChanges(() => {
      setDraftEditContent(selectedFile?.content ?? '');
      setDraftDirty(false);
      setDraftFileDirty(false);
      setDraftEditing(false);
      setDraftWorkingCard(null);
      setDraftEditedPaths([]);
      setActiveTab(tab);
    });
  };

  const handleSubmitVersion = async (version: string) => {
    setActionLoading('submitReview');
    try {
      await workerApi.submitVersion(productId, version);
      message.success(t('product.package.reviewSubmitSuccess', { version }));
      setPreviewVersion(version);
      await Promise.all([fetchVersions(), fetchFileTree(version)]);
      onUploadSuccess?.();
    } catch (error: unknown) {
      const errMsg =
        error instanceof Error ? error.message : t('product.package.reviewSubmitFailed');
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleOfflineVersion = async (version: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.package.offlineConfirm', { version }),
      icon: <ExclamationCircleFilled />,
      okText: t('product.package.offlineOk'),
      okType: 'danger',
      onOk: async () => {
        setActionLoading('offline');
        try {
          await workerApi.offlineVersion(productId, version);
          message.success(t('product.package.offlineSuccess', { version }));
          await fetchVersions();
          onUploadSuccess?.();
        } catch (error: unknown) {
          const errMsg =
            error instanceof Error ? error.message : t('product.package.offlineFailed');
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: t('product.package.offlineTitle'),
    });
  };

  const handleOnlineVersion = async (version: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.package.onlineConfirm', { version }),
      icon: <ExclamationCircleFilled />,
      okText: t('product.package.onlineOk'),
      onOk: async () => {
        setActionLoading('online');
        try {
          await workerApi.onlineVersion(productId, version);
          message.success(t('product.package.onlineSuccess', { version }));
          await Promise.all([fetchVersions(), fetchFileTree(version)]);
          onUploadSuccess?.();
        } catch (error: unknown) {
          const errMsg = error instanceof Error ? error.message : t('product.package.onlineFailed');
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: t('product.package.onlineTitle'),
    });
  };

  const handlePublishApprovedVersion = async (version: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.package.publishApprovedConfirm', { version }),
      icon: <ExclamationCircleFilled />,
      okText: t('product.package.publishApprovedOk'),
      onOk: async () => {
        setActionLoading('publishApproved');
        try {
          await workerApi.publishApprovedVersion(productId, version);
          message.success(t('product.package.publishApprovedSuccess', { version }));
          await Promise.all([fetchVersions(), fetchFileTree(version)]);
          onUploadSuccess?.();
        } catch (error: unknown) {
          const errMsg =
            error instanceof Error ? error.message : t('product.package.publishApprovedFailed');
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: t('product.package.publishApprovedTitle'),
    });
  };

  const handleSetLatest = async (version: string) => {
    setActionLoading('setLatest');
    try {
      await workerApi.setLatestVersion(productId, version);
      message.success(t('product.package.setLatestSuccess', { version }));
      await fetchVersions();
    } catch (error: unknown) {
      const errMsg = error instanceof Error ? error.message : t('product.package.setLatestFailed');
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleCreateDraft = async () => {
    if (!previewVersion || !canCreateDraft) {
      if (createDraftDisabledTip) {
        message.warning(createDraftDisabledTip);
      }
      return;
    }

    const baseVersion = previewVersion;
    setActionLoading('createDraft');
    try {
      await workerApi.createDraft(productId, {
        baseVersion,
      });
      message.success(t('product.package.createDraftSuccess'));
      const res = (await workerApi.getVersions(productId)) as { data?: VersionItem[] };
      const nextVersions = res.data || [];
      setVersions(nextVersions);
      const draftVersion = nextVersions.find((item) => item.status === 'draft')?.version;
      const nextVersion = draftVersion || baseVersion;
      setPreviewVersion(nextVersion);
      await fetchFileTree(nextVersion);
      onUploadSuccess?.();
    } catch (error: unknown) {
      const errMsg =
        error instanceof Error ? error.message : t('product.package.createDraftFailed');
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleOpenAuthorModal = () => {
    if (!previewVersion) return;
    setVersionAuthor(previewItem?.author || '');
    setAuthorModalVisible(true);
  };

  const handleSaveAuthor = async () => {
    if (!previewVersion) return;
    setActionLoading('author');
    try {
      await workerApi.updateVersionAuthor(productId, previewVersion, versionAuthor.trim());
      message.success(t('product.package.saveAuthorSuccess'));
      setAuthorModalVisible(false);
      await fetchVersions();
      onUploadSuccess?.();
    } catch (error: unknown) {
      const errMsg = error instanceof Error ? error.message : t('product.package.saveAuthorFailed');
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleSaveDraft = async () => {
    const nextAgentSpecCard = commitCurrentDraftFile();
    if (!selectedFile || !nextAgentSpecCard) {
      message.error(t('product.package.saveDraftFailed'));
      return;
    }

    setActionLoading('draft');
    try {
      await workerApi.updateDraft(productId, nextAgentSpecCard);
      setDraftAgentSpecCard(nextAgentSpecCard);
      setDraftDirty(false);
      setDraftFileDirty(false);
      setDraftWorkingCard(null);
      setDraftEditedPaths([]);
      setSelectedFile({
        ...selectedFile,
        content:
          getAgentSpecCardFileContent(nextAgentSpecCard, selectedFile.path) ?? draftEditContent,
        size: new Blob([
          getAgentSpecCardFileContent(nextAgentSpecCard, selectedFile.path) ?? draftEditContent,
        ]).size,
      });
      if (overviewPath) {
        const nextOverviewContent = getAgentSpecCardFileContent(nextAgentSpecCard, overviewPath);
        if (nextOverviewContent !== undefined) {
          setOverviewContent(nextOverviewContent);
        }
      }
      setDraftEditing(false);
      message.success(t('product.package.saveDraftSuccess'));
      await fetchVersions(true);
    } catch (error: unknown) {
      const errMsg = error instanceof Error ? error.message : t('product.package.saveDraftFailed');
      message.error(errMsg);
    } finally {
      setActionLoading(null);
    }
  };

  const handleDeleteDraft = async () => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.package.deleteDraftConfirm'),
      icon: <ExclamationCircleFilled />,
      okText: t('product.package.deleteDraftOk'),
      okType: 'danger',
      onOk: async () => {
        setActionLoading('deleteDraft');
        try {
          await workerApi.deleteDraft(productId);
          message.success(t('product.package.deleteDraftSuccess'));
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
          const errMsg =
            error instanceof Error ? error.message : t('product.package.deleteDraftFailed');
          message.error(errMsg);
        } finally {
          setActionLoading(null);
        }
      },
      title: t('product.package.deleteDraftTitle'),
    });
  };

  useEffect(() => {
    const key = `${productId}-${previewVersion ?? ''}`;
    if (lastAutoFileTreeKeyRef.current === key) {
      return;
    }
    lastAutoFileTreeKeyRef.current = key;
    fetchFileTree(previewVersion);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId, previewVersion]);

  useEffect(() => {
    if (lastAutoVersionsProductIdRef.current === productId) {
      return;
    }
    lastAutoVersionsProductIdRef.current = productId;
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

  const customRequest: NonNullable<UploadProps['customRequest']> = async (options) => {
    const { file, onError, onSuccess } = options;
    setUploading(true);
    try {
      // file 可能是 UploadRequestFile 类型 (string | RcFile)，需要转换为 File
      const fileToUpload =
        typeof file === 'string' ? new File([], file) : (file as unknown as File);
      const res = (await workerApi.uploadPackage(productId, fileToUpload)) as { data?: unknown };
      message.success(t('product.package.uploadSuccess'));
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
      const errMsg = error instanceof Error ? error.message : t('product.package.uploadFailed');
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
            <p>{t('product.package.previewEmpty')}</p>
          </div>
        </div>
      );

    if (selectedFile.encoding === 'base64')
      return (
        <div className="flex items-center justify-center h-full text-gray-400">
          <p>{t('product.package.binaryPreviewUnsupported')}</p>
        </div>
      );

    if (showEditDraft && draftEditing) {
      if (loadingDraft || !draftWorkingCard) {
        return (
          <div className="flex items-center justify-center h-full">
            <Spin />
          </div>
        );
      }
      return (
        <div className="flex h-full flex-col bg-white">
          <div className="flex h-11 items-center justify-between gap-3 border-b border-gray-100 bg-gray-50 px-3">
            <div className="min-w-0 truncate text-xs text-gray-500">{selectedFile.path}</div>
            {draftDirty && (
              <span className="text-xs font-medium text-amber-600">
                {t('product.package.unsavedDraft')}
              </span>
            )}
          </div>
          <div className="min-h-0 flex-1">
            <Editor
              height="100%"
              language={getEditorLanguage(selectedFile.path)}
              onChange={(value) => {
                setDraftEditContent(value || '');
                setDraftDirty(true);
                setDraftFileDirty(true);
              }}
              options={{
                automaticLayout: true,
                fontSize: 13,
                minimap: { enabled: false },
                scrollBeyondLastLine: false,
                tabSize: 2,
                wordWrap: 'on',
              }}
              theme="vs-light"
              value={draftEditContent}
            />
          </div>
        </div>
      );
    }

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
        <p className="text-gray-600">{t('product.package.workerDescription')}</p>
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
              {t('nav.nacosInstances')} /{' '}
              {currentNacosName || apiProduct.workerConfig?.nacosId || '-'} /{' '}
              {apiProduct.workerConfig?.namespace || 'public'}
            </Tag>
          </div>
          <Button
            icon={<LinkOutlined />}
            onClick={openNacosModal}
            style={{ background: '#6B5CE7', borderColor: '#6B5CE7' }}
            type="primary"
          >
            {apiProduct.workerConfig?.nacosId
              ? t('product.package.switchNacos')
              : t('product.package.linkNacos')}
          </Button>
        </div>

        {/* Row 1: Version selector + action buttons + upload */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3 flex-wrap">
            <Space.Compact size="large">
              <Select
                className="w-48"
                disabled={draftEditing}
                onChange={(value) => {
                  confirmDiscardDraftChanges(() => setPreviewVersion(value));
                }}
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
                placeholder={t('product.package.versionPlaceholder')}
                value={previewVersion}
              />
              <Button
                className="!bg-gray-100 !text-gray-700 !border-gray-300 hover:!bg-gray-200 disabled:!bg-gray-50 disabled:!text-gray-400"
                disabled={
                  draftEditing || !previewVersion || !isOnline || previewVersion === latestVersion
                }
                loading={actionLoading === 'setLatest'}
                onClick={() => previewVersion && handleSetLatest(previewVersion)}
              >
                {t('product.package.setLatest')}
              </Button>
            </Space.Compact>
            {loadingVersions && <Spin size="small" />}
            {draftEditing ? (
              <>
                <Button
                  disabled={!draftDirty}
                  loading={actionLoading === 'draft'}
                  onClick={handleSaveDraft}
                  type="primary"
                >
                  {t('product.package.saveDraft')}
                </Button>
                <Button danger onClick={cancelDraftEdit}>
                  {t('product.package.cancelDraftEdit')}
                </Button>
              </>
            ) : (
              <>
                {showPublishActions && (
                  <Button
                    disabled={isReviewing}
                    loading={actionLoading === 'submitReview'}
                    onClick={() => previewVersion && handleSubmitVersion(previewVersion)}
                    type="primary"
                  >
                    {t('product.package.submitReview')}
                  </Button>
                )}
                {showEditDraft && (
                  <Tooltip title={!canEditDraft ? editDraftDisabledTip : undefined}>
                    <span>
                      <Button
                        disabled={!canEditDraft}
                        loading={loadingDraft}
                        onClick={startDraftEdit}
                        type="primary"
                      >
                        {t('product.package.editDraft')}
                      </Button>
                    </span>
                  </Tooltip>
                )}
                {(canDeleteDraft || isReviewing) && (
                  <Button
                    danger
                    disabled={isReviewing}
                    loading={actionLoading === 'deleteDraft'}
                    onClick={handleDeleteDraft}
                  >
                    {t('product.package.deleteDraft')}
                  </Button>
                )}
              </>
            )}
            {canPublishApproved && (
              <Button
                disabled={draftEditing}
                loading={actionLoading === 'publishApproved'}
                onClick={() => previewVersion && handlePublishApprovedVersion(previewVersion)}
                type="primary"
              >
                {t('product.package.publishApprovedOk')}
              </Button>
            )}
            {canOnline && (
              <Button
                disabled={draftEditing}
                loading={actionLoading === 'online'}
                onClick={() => previewVersion && handleOnlineVersion(previewVersion)}
                type="primary"
              >
                {t('product.package.versionOnline')}
              </Button>
            )}
            {canOffline && (
              <Button
                danger
                disabled={draftEditing}
                loading={actionLoading === 'offline'}
                onClick={() => previewVersion && handleOfflineVersion(previewVersion)}
              >
                {t('product.package.versionOffline')}
              </Button>
            )}
            {canCreateDraft && (
              <Tooltip title={t('product.package.createDraftBaseVersionTip')}>
                <Button
                  disabled={draftEditing}
                  loading={actionLoading === 'createDraft'}
                  onClick={handleCreateDraft}
                  type="primary"
                >
                  {t('product.package.createDraft')}
                </Button>
              </Tooltip>
            )}
          </div>

          <Upload
            accept=".zip"
            capture={undefined}
            customRequest={customRequest}
            disabled={uploading || !hasNacos || draftEditing}
            hasControlInside={false}
            pastable={false}
            showUploadList={false}
          >
            <Button
              className="!h-auto !px-4 !py-2.5"
              disabled={!hasNacos || draftEditing}
              icon={<UploadOutlined />}
              loading={uploading}
              style={
                !hasNacos ? { background: '#f5f5f5', borderColor: '#d9d9d9', color: '#bfbfbf' } : {}
              }
            >
              <div className="leading-snug text-left">
                <div className="text-sm">{t('product.package.uploadWorker')}</div>
                <div className="text-xs text-gray-400">{t('product.package.uploadWorkerHint')}</div>
              </div>
            </Button>
          </Upload>
        </div>

        {/* Row 2: Status + review result + downloads + author */}
        <div className="flex flex-wrap items-center gap-2 text-sm" style={{ minHeight: 32 }}>
          <span className={statusInfoChipClass}>{statusText}</span>
          {(() => {
            const pStatus = pipelineStatus?.status;
            const hasReviewInfo = !!pipelineStatus;
            // Review info is independent from lifecycle status such as online/offline.
            const isApproved = pStatus === 'APPROVED';
            const isRejected = pStatus === 'REJECTED';
            const isInProgress = hasReviewInfo && !isApproved && !isRejected;
            const pipelineNodes = pipelineStatus?.pipeline as PipelineNode[] | undefined;

            if (isInProgress) {
              return (
                <span className={`${infoChipClass} border-blue-100 bg-blue-50 text-blue-600`}>
                  <Spin size="small" />
                  {pipelineNodes && pipelineNodes.length > 0 && (
                    <span className="text-blue-400">
                      ({pipelineNodes.filter((n) => n.passed).length}/{pipelineNodes.length})
                    </span>
                  )}
                </span>
              );
            }
            if (isApproved) {
              return (
                <span className={`${infoChipClass} border-green-100 bg-green-50 text-green-600`}>
                  <CheckCircleFilled />
                  <span className="font-medium">{t('product.package.reviewApproved')}</span>
                </span>
              );
            }
            if (isRejected) {
              return (
                <span className={`${infoChipClass} border-red-100 bg-red-50 text-red-500`}>
                  <CloseCircleFilled />
                  <span className="font-medium">{t('product.package.reviewRejected')}</span>
                  <Button
                    className="!h-auto !p-0 !text-red-500 !text-xs !leading-none"
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
                                            {new Date(node.executedAt).toLocaleString(locale)}
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
                        title: t('product.package.reviewRejected'),
                        width: 600,
                      })
                    }
                    size="small"
                    type="link"
                  >
                    {t('product.package.reviewDetail')}
                  </Button>
                </span>
              );
            }
            return null;
          })()}
          <span className={neutralInfoChipClass}>
            <span>{t('product.package.downloads')}</span>
            <strong className="font-semibold text-gray-700">{totalDownloads}</strong>
          </span>
          {previewVersion && (
            <span className={neutralInfoChipClass}>
              <span>{t('product.package.author')}</span>
              <Tooltip
                title={
                  previewItem?.author
                    ? t('product.package.editAuthor')
                    : t('product.package.setAuthor')
                }
              >
                <button
                  aria-label={
                    previewItem?.author
                      ? t('product.package.editAuthor')
                      : t('product.package.setAuthor')
                  }
                  className={`group inline-flex items-center gap-1 rounded-sm transition-colors hover:text-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-blue-400 ${
                    previewItem?.author
                      ? 'font-semibold text-gray-700'
                      : 'font-medium text-gray-500'
                  }`}
                  onClick={handleOpenAuthorModal}
                  type="button"
                >
                  <span>{previewItem?.author || ''}</span>
                  <EditOutlined className="text-[11px] text-gray-400 transition-colors group-hover:text-blue-500" />
                </button>
              </Tooltip>
            </span>
          )}
        </div>
      </div>

      <PackageContentPanel
        activeTab={activeTab}
        fileTree={fileTree}
        loadingOverview={loadingOverview}
        loadingTree={loadingTree}
        noFilesText={t('product.package.noFiles')}
        onFileSelect={(path) => loadFileContent(path, previewVersion)}
        onResizeStart={handleDragStart}
        onTabChange={switchContentTab}
        overviewContent={overviewContent}
        overviewEmptyText={t('product.package.workerMissingOverview')}
        renderPreview={renderPreview}
        selectedPath={selectedPath}
        treeWidth={treeWidth}
      />

      {/* Version Author Modal */}
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={actionLoading === 'author'}
        okText={t('product.package.saveAuthor')}
        onCancel={() => setAuthorModalVisible(false)}
        onOk={handleSaveAuthor}
        open={authorModalVisible}
        title={
          previewItem?.author ? t('product.package.editAuthor') : t('product.package.setAuthor')
        }
      >
        <Form layout="vertical">
          <Form.Item label={t('product.package.author')}>
            <Input
              allowClear
              maxLength={64}
              onChange={(event) => setVersionAuthor(event.target.value)}
              placeholder={t('product.package.authorPlaceholder')}
              value={versionAuthor}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Nacos Modal */}
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={nacosSaving}
        okText={t('common.confirm')}
        onCancel={() => setNacosModalVisible(false)}
        onOk={handleNacosSave}
        open={nacosModalVisible}
        title={t('product.package.nacosTitle')}
      >
        <Form form={nacosForm} layout="vertical">
          <Form.Item
            label={t('product.package.nacosInstance')}
            name="nacosId"
            rules={[{ message: t('product.package.nacosRequired'), required: true }]}
          >
            <Select
              loading={nacosLoading}
              onChange={handleNacosChange}
              options={nacosInstances.map((n) => ({
                label: `${n.nacosName}${
                  n.isDefault ? ` (${t('product.package.nacosDefault')})` : ''
                }`,
                value: n.nacosId,
              }))}
              placeholder={t('product.package.selectNacos')}
            />
          </Form.Item>
          <Form.Item
            label={t('product.package.namespace')}
            name="namespace"
            rules={[{ message: t('product.package.namespaceRequired'), required: true }]}
          >
            <Select
              loading={nsLoading}
              options={nacosNsOptions.map((ns) => ({
                label: ns.namespaceName || ns.namespaceId || 'public',
                value: ns.namespaceId || '',
              }))}
              placeholder={t('product.package.selectNamespace')}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
