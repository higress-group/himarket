import { Spin } from 'antd';

import { PackageFileTree } from './PackageFileTree';
import { PackageOverview } from './PackageOverview';

import type { PackageFileTreeNode } from './types';
import type { MouseEvent, ReactNode } from 'react';

interface PackageContentPanelProps {
  activeTab: 'overview' | 'file';
  fileTree: PackageFileTreeNode[];
  loadingOverview: boolean;
  loadingTree: boolean;
  noFilesText: string;
  overviewContent: string | null;
  overviewEmptyText: string;
  renderPreview: () => ReactNode;
  selectedPath?: string;
  treeWidth: number;
  onFileSelect: (path: string) => void;
  onResizeStart: (event: MouseEvent<HTMLDivElement>) => void;
  onTabChange: (tab: 'overview' | 'file') => void;
}

export function PackageContentPanel({
  activeTab,
  fileTree,
  loadingOverview,
  loadingTree,
  noFilesText,
  onFileSelect,
  onResizeStart,
  onTabChange,
  overviewContent,
  overviewEmptyText,
  renderPreview,
  selectedPath,
  treeWidth,
}: PackageContentPanelProps) {
  return (
    <div
      className="flex flex-1 flex-col overflow-hidden rounded-lg border bg-white"
      style={{ minHeight: 600 }}
    >
      <div className="flex gap-6 border-b px-4 pt-3">
        <button
          className={`border-b-2 pb-2 text-sm font-medium transition-colors ${
            activeTab === 'overview'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => onTabChange('overview')}
          type="button"
        >
          Overview
        </button>
        <button
          className={`border-b-2 pb-2 text-sm font-medium transition-colors ${
            activeTab === 'file'
              ? 'border-blue-600 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => onTabChange('file')}
          type="button"
        >
          File
        </button>
      </div>

      {activeTab === 'overview' ? (
        <div className="flex-1 overflow-auto p-6" style={{ height: 560 }}>
          {loadingOverview ? (
            <div className="flex justify-center pt-8">
              <Spin size="small" />
            </div>
          ) : overviewContent ? (
            <PackageOverview content={overviewContent} />
          ) : (
            <div className="pt-8 text-center text-sm text-gray-400">{overviewEmptyText}</div>
          )}
        </div>
      ) : (
        <div className="flex min-h-0 flex-1" style={{ height: 560 }}>
          <div
            className="flex-shrink-0 overflow-y-auto overflow-x-hidden border-r bg-white p-2"
            style={{ width: treeWidth }}
          >
            {loadingTree ? (
              <div className="flex h-full items-center justify-center">
                <Spin size="small" />
              </div>
            ) : fileTree.length === 0 ? (
              <div className="flex h-full items-center justify-center text-sm text-gray-400">
                {noFilesText}
              </div>
            ) : (
              <PackageFileTree
                nodes={fileTree}
                onSelect={onFileSelect}
                selectedPath={selectedPath}
              />
            )}
          </div>
          <div
            className="w-1 flex-shrink-0 cursor-col-resize bg-transparent transition-colors hover:bg-blue-200"
            onMouseDown={onResizeStart}
            role="slider"
            tabIndex={0}
          />
          <div className="flex flex-1 flex-col overflow-auto" style={{ height: 560 }}>
            {renderPreview()}
          </div>
        </div>
      )}
    </div>
  );
}
