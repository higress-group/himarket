import type { FileNode } from '../../types/coding';

export interface FlatFileItem {
  name: string; // Filename only
  path: string; // Absolute path
  relativePath: string; // Path relative to workspace root
  extension?: string; // File extension
}

/**
 * Flatten hierarchical FileNode tree into a flat array of files (directories excluded)
 */
export function flattenFileTree(nodes: FileNode[], basePath: string): FlatFileItem[] {
  const result: FlatFileItem[] = [];

  function traverse(node: FileNode) {
    if (node.type === 'file') {
      const relativePath = node.path.startsWith(basePath)
        ? node.path.slice(basePath.length).replace(/^\//, '')
        : node.path;

      result.push({
        extension: node.extension,
        name: node.name,
        path: node.path,
        relativePath,
      });
    }

    if (node.children) {
      node.children.forEach(traverse);
    }
  }

  nodes.forEach(traverse);
  return result;
}

/**
 * Filter files by substring matching (case-insensitive)
 * Prioritizes: exact match > starts with > contains
 */
export function filterFiles(files: FlatFileItem[], query: string, limit = 50): FlatFileItem[] {
  if (!query) {
    return files.slice(0, limit);
  }

  const lowerQuery = query.toLowerCase();

  // Categorize matches
  const exactMatches: FlatFileItem[] = [];
  const startsWithMatches: FlatFileItem[] = [];
  const containsMatches: FlatFileItem[] = [];

  for (const file of files) {
    const lowerName = file.name.toLowerCase();
    const lowerRelPath = file.relativePath.toLowerCase();

    if (lowerName === lowerQuery || lowerRelPath === lowerQuery) {
      exactMatches.push(file);
    } else if (lowerName.startsWith(lowerQuery) || lowerRelPath.startsWith(lowerQuery)) {
      startsWithMatches.push(file);
    } else if (lowerName.includes(lowerQuery) || lowerRelPath.includes(lowerQuery)) {
      containsMatches.push(file);
    }
  }

  // Combine results with priority
  return [...exactMatches, ...startsWithMatches, ...containsMatches].slice(0, limit);
}
