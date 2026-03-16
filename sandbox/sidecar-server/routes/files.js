import fs from 'node:fs/promises';
import fsSync from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';
import { parseJsonBody, sendJson } from '../lib/http.js';
import { resolvePath } from '../lib/path.js';
import { WORKSPACE_ROOT } from '../config.js';

// ---------------------------------------------------------------------------
// 文件操作端点
// 设计参考 OpenSandbox execd：所有路径参数接受绝对路径，
// 由调用方（后端 Java 服务）负责构建正确的用户隔离路径。
// ---------------------------------------------------------------------------

export const fileRoutes = [
  // POST /files/write — 写入文件（支持绝对路径）
  // 支持 encoding 参数：'base64' 时将 content 从 base64 解码后写入（用于二进制文件），默认 'utf-8'
  {
    method: 'POST',
    match: (url) => url === '/files/write',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { path: filePath, content, encoding: reqEncoding } = body;
      if (!filePath || content === undefined || content === null) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 path 或 content 参数',
        });
      }
      try {
        const fullPath = resolvePath(filePath);
        await fs.mkdir(path.dirname(fullPath), { recursive: true });
        if (reqEncoding === 'base64') {
          await fs.writeFile(fullPath, Buffer.from(content, 'base64'));
        } else {
          await fs.writeFile(fullPath, content, 'utf-8');
        }
        sendJson(res, 200, { success: true });
      } catch (err) {
        sendJson(res, 500, { success: false, error: err.message });
      }
    },
  },

  // POST /files/read — 读取文件（支持绝对路径）
  // 支持 encoding 参数：'base64' 返回 base64 编码（用于二进制文件），默认 'utf-8'
  {
    method: 'POST',
    match: (url) => url === '/files/read',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { path: filePath, encoding: reqEncoding } = body;
      if (!filePath) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 path 参数',
        });
      }
      try {
        const fullPath = resolvePath(filePath);
        if (reqEncoding === 'base64') {
          const buffer = await fs.readFile(fullPath);
          sendJson(res, 200, {
            content: buffer.toString('base64'),
            encoding: 'base64',
          });
        } else {
          const content = await fs.readFile(fullPath, 'utf-8');
          sendJson(res, 200, { content, encoding: 'utf-8' });
        }
      } catch (err) {
        const status = err.code === 'ENOENT' ? 404 : 500;
        sendJson(res, status, { success: false, error: err.message });
      }
    },
  },

  // GET /files/download?path=xxx — 下载文件原始二进制流
  // 兼容 OpenSandbox execd 的 /files/download 端点设计，
  // 直接返回文件原始字节，不经过 JSON/base64 编码，适用于二进制文件下载。
  {
    method: 'GET',
    match: (url) =>
      url === '/files/download' || url.startsWith('/files/download?'),
    handler: async (req, res) => {
      const reqUrl = new URL(
        req.url,
        `http://${req.headers.host || 'localhost'}`,
      );
      const filePath = reqUrl.searchParams.get('path');
      if (!filePath) {
        return sendJson(res, 400, {
          success: false,
          error: "missing query parameter 'path'",
        });
      }
      try {
        const fullPath = resolvePath(filePath);
        const stat = await fs.stat(fullPath);
        if (!stat.isFile()) {
          return sendJson(res, 400, {
            success: false,
            error: '指定路径不是文件',
          });
        }
        const fileName = path.basename(fullPath);
        res.writeHead(200, {
          'Content-Type': 'application/octet-stream',
          'Content-Disposition': `attachment; filename="${fileName}"`,
          'Content-Length': stat.size,
        });
        fsSync.createReadStream(fullPath).pipe(res);
      } catch (err) {
        const status = err.code === 'ENOENT' ? 404 : 500;
        sendJson(res, status, { success: false, error: err.message });
      }
    },
  },

  // POST /files/mkdir — 创建目录（支持绝对路径）
  {
    method: 'POST',
    match: (url) => url === '/files/mkdir',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { path: dirPath } = body;
      if (!dirPath) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 path 参数',
        });
      }
      try {
        const fullPath = resolvePath(dirPath);
        await fs.mkdir(fullPath, { recursive: true });
        sendJson(res, 200, { success: true });
      } catch (err) {
        sendJson(res, 500, { success: false, error: err.message });
      }
    },
  },

  // POST /files/extract — 接收 tar.gz 二进制流，解压到指定目录
  // 支持 query 参数 ?cwd=/workspace/dev-xxx 指定解压目标目录
  // 未指定时降级为 WORKSPACE_ROOT（向后兼容）
  {
    method: 'POST',
    match: (url) =>
      url === '/files/extract' || url.startsWith('/files/extract?'),
    handler: (req, res) => {
      const reqUrl = new URL(
        req.url,
        `http://${req.headers.host || 'localhost'}`,
      );
      const extractCwd = reqUrl.searchParams.get('cwd') || WORKSPACE_ROOT;

      const chunks = [];
      req.on('data', (chunk) => chunks.push(chunk));
      req.on('end', async () => {
        try {
          const buffer = Buffer.concat(chunks);
          if (buffer.length === 0) {
            return sendJson(res, 400, {
              success: false,
              error: '请求体为空',
            });
          }

          // 确保目标目录存在
          if (!fsSync.existsSync(extractCwd)) {
            fsSync.mkdirSync(extractCwd, { recursive: true });
          }

          // 写入临时文件，用 tar 命令解压
          const tmpFile = path.join(
            extractCwd,
            '.tmp-extract-' + Date.now() + '.tar.gz',
          );
          await fs.writeFile(tmpFile, buffer);
          try {
            execSync(`tar xzf "${tmpFile}" 2>&1`, {
              cwd: extractCwd,
              timeout: 30000,
              maxBuffer: 1024 * 1024,
            });
            // 统计解压的文件数
            let fileCount = 0;
            try {
              const listOutput = execSync(
                `tar tzf "${tmpFile}" 2>/dev/null`,
                {
                  cwd: extractCwd,
                  timeout: 10000,
                  maxBuffer: 1024 * 1024,
                },
              )
                .toString()
                .trim();
              fileCount = listOutput
                ? listOutput.split('\n').filter((l) => !l.endsWith('/')).length
                : 0;
            } catch {
              fileCount = -1;
            }
            sendJson(res, 200, { success: true, fileCount });
          } finally {
            await fs.unlink(tmpFile).catch(() => {});
          }
        } catch (err) {
          sendJson(res, 500, { success: false, error: err.message });
        }
      });
      req.on('error', (err) => {
        sendJson(res, 500, { success: false, error: err.message });
      });
    },
  },

  // POST /files/list — 列出目录内容（支持绝对路径）
  {
    method: 'POST',
    match: (url) => url === '/files/list',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { path: dirPath, depth } = body;
      if (!dirPath) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 path 参数',
        });
      }
      const maxDepth =
        typeof depth === 'number' && depth > 0 ? depth : 3;
      try {
        const fullPath = resolvePath(dirPath);
        const stat = await fs.stat(fullPath);
        if (!stat.isDirectory()) {
          return sendJson(res, 400, {
            success: false,
            error: '指定路径不是目录',
          });
        }

        async function buildTree(currentPath, currentDepth) {
          const entries = await fs.readdir(currentPath, {
            withFileTypes: true,
          });
          const result = [];
          for (const entry of entries) {
            if (entry.isFile()) {
              result.push({ name: entry.name, type: 'file' });
            } else if (entry.isDirectory()) {
              const node = { name: entry.name, type: 'dir', children: [] };
              if (currentDepth < maxDepth) {
                node.children = await buildTree(
                  path.join(currentPath, entry.name),
                  currentDepth + 1,
                );
              }
              result.push(node);
            }
          }
          return result;
        }

        const tree = await buildTree(fullPath, 1);
        sendJson(res, 200, tree);
      } catch (err) {
        if (err.code === 'ENOENT') {
          return sendJson(res, 404, {
            success: false,
            error: '路径不存在: ' + dirPath,
          });
        }
        sendJson(res, 500, { success: false, error: err.message });
      }
    },
  },

  // POST /files/changes — 查询文件变更（支持绝对路径）
  {
    method: 'POST',
    match: (url) => url === '/files/changes',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { cwd: cwdPath, since } = body;
      if (!cwdPath) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 cwd 参数',
        });
      }
      const sinceMs =
        typeof since === 'number' && since > 0 ? since : 0;
      const MAX_DEPTH = 10;
      const SKIP_DIRS = new Set([
        'node_modules',
        '.git',
        '.next',
        'dist',
        '__pycache__',
        '.cache',
      ]);
      try {
        const fullPath = resolvePath(cwdPath);
        const changes = [];

        async function scan(dir, depth) {
          if (depth > MAX_DEPTH) return;
          let entries;
          try {
            entries = await fs.readdir(dir, { withFileTypes: true });
          } catch {
            return;
          }
          for (const entry of entries) {
            const entryPath = path.join(dir, entry.name);
            if (entry.isDirectory()) {
              if (!SKIP_DIRS.has(entry.name)) {
                await scan(entryPath, depth + 1);
              }
            } else if (entry.isFile()) {
              try {
                const stat = await fs.stat(entryPath);
                if (stat.mtimeMs > sinceMs) {
                  changes.push({
                    path: path.relative(fullPath, entryPath),
                    mtimeMs: stat.mtimeMs,
                    size: stat.size,
                    ext: path.extname(entry.name),
                  });
                }
              } catch {
                /* skip inaccessible files */
              }
            }
          }
        }

        await scan(fullPath, 0);
        sendJson(res, 200, { changes });
      } catch (err) {
        sendJson(res, 500, { success: false, error: err.message });
      }
    },
  },

  // POST /files/exists — 检查文件是否存在（支持绝对路径）
  {
    method: 'POST',
    match: (url) => url === '/files/exists',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, {
          success: false,
          error: '无效的 JSON 请求体',
        });
      }
      const { path: filePath } = body;
      if (!filePath) {
        return sendJson(res, 400, {
          success: false,
          error: '缺少 path 参数',
        });
      }
      try {
        const fullPath = resolvePath(filePath);
        const stat = await fs.stat(fullPath);
        sendJson(res, 200, {
          exists: true,
          isFile: stat.isFile(),
          isDirectory: stat.isDirectory(),
        });
      } catch (err) {
        if (err.code === 'ENOENT') {
          sendJson(res, 200, {
            exists: false,
            isFile: false,
            isDirectory: false,
          });
        } else {
          sendJson(res, 500, { success: false, error: err.message });
        }
      }
    },
  },
];
