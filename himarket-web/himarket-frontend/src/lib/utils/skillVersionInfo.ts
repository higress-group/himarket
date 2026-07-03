import type { ISkillConfig, IVersionInfo, IWorkerConfig } from '../apis/typing';

interface VersionAuthorConfig {
  latestVersion?: string;
  versionInfos?: Record<string, IVersionInfo | null>;
}

interface VersionWithAuthor {
  author?: string | null;
  version: string;
}

function normalizeAuthor(author?: string | null): string | undefined {
  const value = author?.trim();
  return value ? value.replace(/^@+/, '') : undefined;
}

export function formatSkillAuthor(author?: string | null): string | undefined {
  const normalized = normalizeAuthor(author);
  return normalized ? `@${normalized}` : undefined;
}

export function getSelectedSkillVersionAuthor(
  versions: VersionWithAuthor[],
  selectedVersion?: string,
): string | undefined {
  const version = versions.find((item) => item.version === selectedVersion);
  return normalizeAuthor(version?.author);
}

export function getLatestVersionAuthor(config?: VersionAuthorConfig): string | undefined {
  const versionInfos = config?.versionInfos;
  if (!versionInfos) {
    return undefined;
  }

  if (!config.latestVersion) {
    return undefined;
  }

  return normalizeAuthor(versionInfos[config.latestVersion]?.author);
}

export function getSkillLatestAuthor(config?: ISkillConfig): string | undefined {
  return getLatestVersionAuthor(config);
}

export function getWorkerLatestAuthor(config?: IWorkerConfig): string | undefined {
  return getLatestVersionAuthor(config);
}
