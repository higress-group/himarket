import {
  CopyOutlined,
  CheckOutlined,
  GithubOutlined,
  ShareAltOutlined,
  HeartOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { message, Tooltip } from 'antd';
import { useState } from 'react';

import { parseSkillMd } from '../../lib/skillMdUtils';
import { copyToClipboard } from '../../lib/utils';

interface InstallCommandProps {
  productId: string;
  skillName: string;
  document: string;
}

type PackageManager = 'npx' | 'bunx' | 'pnpm';

function InstallCommand({ document, productId, skillName }: InstallCommandProps) {
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [activePM, setActivePM] = useState<PackageManager>('pnpm');

  const parsed = parseSkillMd(document);
  const fm = parsed.frontmatter;

  const author = fm.author || fm.owner || '';
  const repository = fm.repository || fm.repo || '';
  const dirName = fm.name || skillName.toLowerCase().replace(/\s+/g, '-');
  const skillPath = author ? `${author}/${dirName}` : dirName;

  const handleDownloadPackage = async () => {
    try {
      const headers: Record<string, string> = {};
      const token = localStorage.getItem('access_token');
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
      const res = await fetch(`/api/v1/skills/${productId}/download`, { headers });
      if (!res.ok) throw new Error('下载失败');
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = window.document.createElement('a');
      a.href = blobUrl;
      const disposition = res.headers.get('Content-Disposition') ?? '';
      const match = disposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i);
      a.download = match && match[1] ? decodeURIComponent(match[1]) : `${dirName}.zip`;
      a.click();
      URL.revokeObjectURL(blobUrl);
    } catch {
      message.error('下载失败');
    }
  };

  const installCommands: Record<PackageManager, string> = {
    bunx: `bunx skills add ${skillPath}`,
    npx: `npx skills add ${skillPath}`,
    pnpm: `pnpm dlx skills add ${skillPath}`,
  };

  const handleCopy = async (text: string, id: string) => {
    try {
      await copyToClipboard(text);
      message.success('已复制到剪贴板');
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      message.error('复制失败');
    }
  };

  const CopyBtn = ({ id, text }: { id: string; text: string }) => (
    <button
      className="p-1 rounded hover:bg-gray-100 transition-colors"
      onClick={() => handleCopy(text, id)}
    >
      {copiedId === id ? (
        <CheckOutlined className="text-green-500 text-xs" />
      ) : (
        <CopyOutlined className="text-gray-400 hover:text-gray-600 text-xs" />
      )}
    </button>
  );

  return (
    <div className="space-y-4">
      {/* 卡片1: 作者与仓库信息 */}
      <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-gray-900">基本信息</h3>
          <div className="flex items-center gap-2">
            <Tooltip title="分享">
              <ShareAltOutlined className="text-gray-400 hover:text-gray-600 cursor-pointer text-sm" />
            </Tooltip>
            <Tooltip title="收藏">
              <HeartOutlined className="text-gray-400 hover:text-gray-600 cursor-pointer text-sm" />
            </Tooltip>
          </div>
        </div>

        <div className="space-y-3">
          {/* 作者信息 */}
          {author && (
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-full bg-gradient-to-br from-purple-500 to-indigo-500 flex items-center justify-center flex-shrink-0">
                <span className="text-white text-sm font-bold">
                  {author.charAt(0).toUpperCase()}
                </span>
              </div>
              <div>
                <div className="text-sm font-medium text-gray-800">{author}</div>
                <div className="text-xs text-gray-400">作者</div>
              </div>
            </div>
          )}

          {/* 仓库信息 */}
          {repository && (
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-full bg-gray-100 flex items-center justify-center flex-shrink-0">
                <GithubOutlined className="text-gray-600 text-base" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm text-gray-700 truncate">{repository}</div>
                <div className="text-xs text-gray-400">仓库</div>
              </div>
            </div>
          )}

          {!author && !repository && (
            <div className="text-sm text-gray-400 text-center py-2">暂无作者信息</div>
          )}

          {/* 查看仓库按钮 */}
          {repository && (
            <button
              className="
                flex items-center justify-center gap-2 w-full px-4 py-2
                rounded-lg border border-gray-200 text-sm
                text-gray-700 bg-white hover:bg-gray-50
                transition-colors duration-200
              "
              onClick={() => {
                const url = repository.startsWith('http')
                  ? repository
                  : `https://github.com/${repository}`;
                window.open(url, '_blank');
              }}
            >
              <GithubOutlined />
              <span>查看仓库</span>
            </button>
          )}
        </div>
      </div>

      {/* 卡片2: 全局安装 */}
      <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-5">
        <h3 className="text-base font-semibold text-gray-900 mb-4">全局安装</h3>

        {/* 包管理器切换 */}
        <div className="flex rounded-lg border border-gray-200 overflow-hidden text-xs mb-3">
          {(['npx', 'bunx', 'pnpm'] as PackageManager[]).map((pm) => (
            <button
              className={`flex-1 px-3 py-1.5 transition-colors duration-200 ${
                activePM === pm
                  ? 'bg-purple-100 text-purple-700 font-medium'
                  : 'bg-white text-gray-500 hover:bg-gray-50'
              }`}
              key={pm}
              onClick={() => setActivePM(pm)}
            >
              {pm}
            </button>
          ))}
        </div>

        {/* 命令展示 */}
        <div className="bg-gray-50 rounded-lg px-4 py-3 flex items-center justify-between gap-2">
          <code className="text-sm text-gray-800 break-all leading-relaxed">
            {installCommands[activePM]}
          </code>
          <CopyBtn id="install" text={installCommands[activePM]} />
        </div>
      </div>

      {/* 卡片3: 本地下载 */}
      <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-gray-900">本地下载</h3>
          <Tooltip title="下载包含 SKILL.md 和所有相关文件的完整技能目录">
            <InfoCircleOutlined className="text-gray-400 text-sm" />
          </Tooltip>
        </div>

        <div className="space-y-2">
          <button
            className="
              flex items-center justify-center gap-2 w-full px-4 py-2.5
              rounded-lg text-sm font-medium text-white
              bg-gradient-to-r from-purple-500 to-indigo-500
              hover:from-purple-600 hover:to-indigo-600
              transition-all duration-200 shadow-sm
            "
            onClick={handleDownloadPackage}
          >
            <DownloadOutlined />
            <span>下载技能包</span>
          </button>

          <button
            className="
              flex items-center justify-center gap-2 w-full px-4 py-2
              rounded-lg border border-gray-200 text-sm
              text-gray-700 bg-white hover:bg-gray-50
              transition-colors duration-200
            "
            onClick={() => handleCopy(document, 'content')}
          >
            {copiedId === 'content' ? (
              <CheckOutlined className="text-green-500" />
            ) : (
              <CopyOutlined />
            )}
            <span>复制 SKILL.md 内容</span>
          </button>

          <p className="text-xs text-gray-400 pt-1">
            下载包含 SKILL.md 和所有相关文件的完整技能目录
          </p>
        </div>
      </div>
    </div>
  );
}

export default InstallCommand;
