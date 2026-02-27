'use strict';

const http = require('http');
const fs = require('fs/promises');
const nodePath = require('path');
const os = require('os');

// ---------------------------------------------------------------------------
// 测试辅助
// ---------------------------------------------------------------------------

let testPort;
let testWorkspaceRoot;
let testServer;

/**
 * 向测试 server 发送 HTTP 请求
 */
function request(method, urlPath, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : '';
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: testPort,
        path: urlPath,
        method,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
        },
      },
      (res) => {
        let responseBody = '';
        res.on('data', (chunk) => { responseBody += chunk; });
        res.on('end', () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(responseBody) });
          } catch {
            resolve({ status: res.statusCode, body: responseBody });
          }
        });
      }
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

/**
 * 创建独立的 sidecar HTTP server 用于测试，避免端口冲突。
 */
function createTestServer(workspaceRoot) {
  const path = nodePath;

  function resolveSafePath(relativePath) {
    const resolved = path.resolve(workspaceRoot, relativePath);
    const root = path.resolve(workspaceRoot);
    if (!resolved.startsWith(root + path.sep) && resolved !== root) {
      throw new Error('路径越界: ' + relativePath);
    }
    return resolved;
  }

  function parseJsonBody(req) {
    return new Promise((resolve, reject) => {
      let body = '';
      req.on('data', (chunk) => { body += chunk; });
      req.on('end', () => {
        try { resolve(body ? JSON.parse(body) : {}); }
        catch (err) { reject(err); }
      });
      req.on('error', reject);
    });
  }

  function sendJson(res, statusCode, data) {
    const body = JSON.stringify(data);
    res.writeHead(statusCode, { 'Content-Type': 'application/json' });
    res.end(body);
  }

  const server = http.createServer(async (req, res) => {
    if (req.method === 'POST' && req.url === '/files/write') {
      let body;
      try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
      const { path: filePath, content } = body;
      if (!filePath || content === undefined || content === null) {
        return sendJson(res, 400, { success: false, error: '缺少 path 或 content 参数' });
      }
      try {
        const fullPath = resolveSafePath(filePath);
        await fs.mkdir(path.dirname(fullPath), { recursive: true });
        await fs.writeFile(fullPath, content, 'utf-8');
        sendJson(res, 200, { success: true });
      } catch (err) {
        if (err.message.startsWith('路径越界')) {
          return sendJson(res, 403, { success: false, error: err.message });
        }
        sendJson(res, 500, { success: false, error: err.message });
      }
      return;
    }

    if (req.method === 'POST' && req.url === '/files/read') {
      let body;
      try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
      const { path: filePath } = body;
      if (!filePath) {
        return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
      }
      try {
        const fullPath = resolveSafePath(filePath);
        const content = await fs.readFile(fullPath, 'utf-8');
        sendJson(res, 200, { content });
      } catch (err) {
        if (err.message.startsWith('路径越界')) {
          return sendJson(res, 403, { success: false, error: err.message });
        }
        const status = err.code === 'ENOENT' ? 404 : 500;
        sendJson(res, status, { success: false, error: err.message });
      }
      return;
    }

    if (req.method === 'POST' && req.url === '/files/mkdir') {
      let body;
      try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
      const { path: dirPath } = body;
      if (!dirPath) {
        return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
      }
      try {
        const fullPath = resolveSafePath(dirPath);
        await fs.mkdir(fullPath, { recursive: true });
        sendJson(res, 200, { success: true });
      } catch (err) {
        if (err.message.startsWith('路径越界')) {
          return sendJson(res, 403, { success: false, error: err.message });
        }
        sendJson(res, 500, { success: false, error: err.message });
      }
      return;
    }

    if (req.method === 'POST' && req.url === '/files/list') {
      let body;
      try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
      const { path: dirPath, depth } = body;
      if (!dirPath) {
        return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
      }
      const maxDepth = (typeof depth === 'number' && depth > 0) ? depth : 3;
      try {
        const fullPath = resolveSafePath(dirPath);
        const stat = await fs.stat(fullPath);
        if (!stat.isDirectory()) {
          return sendJson(res, 400, { success: false, error: '指定路径不是目录' });
        }

        async function buildTree(currentPath, currentDepth) {
          const entries = await fs.readdir(currentPath, { withFileTypes: true });
          const result = [];
          for (const entry of entries) {
            if (entry.isFile()) {
              result.push({ name: entry.name, type: 'file' });
            } else if (entry.isDirectory()) {
              const node = { name: entry.name, type: 'dir', children: [] };
              if (currentDepth < maxDepth) {
                node.children = await buildTree(path.join(currentPath, entry.name), currentDepth + 1);
              }
              result.push(node);
            }
          }
          return result;
        }

        const tree = await buildTree(fullPath, 1);
        sendJson(res, 200, tree);
      } catch (err) {
        if (err.message.startsWith('路径越界')) {
          return sendJson(res, 403, { success: false, error: err.message });
        }
        if (err.code === 'ENOENT') {
          return sendJson(res, 404, { success: false, error: '路径不存在: ' + dirPath });
        }
        sendJson(res, 500, { success: false, error: err.message });
      }
      return;
    }

    if (req.method === 'POST' && req.url === '/files/exists') {
      let body;
      try { body = await parseJsonBody(req); } catch { return sendJson(res, 400, { success: false, error: '无效的 JSON 请求体' }); }
      const { path: filePath } = body;
      if (!filePath) {
        return sendJson(res, 400, { success: false, error: '缺少 path 参数' });
      }
      try {
        const fullPath = resolveSafePath(filePath);
        const stat = await fs.stat(fullPath);
        sendJson(res, 200, { exists: true, isFile: stat.isFile(), isDirectory: stat.isDirectory() });
      } catch (err) {
        if (err.message.startsWith('路径越界')) {
          return sendJson(res, 403, { success: false, error: err.message });
        }
        if (err.code === 'ENOENT') {
          sendJson(res, 200, { exists: false, isFile: false, isDirectory: false });
        } else {
          sendJson(res, 500, { success: false, error: err.message });
        }
      }
      return;
    }

    res.writeHead(404);
    res.end('Not Found');
  });

  return server;
}

// ---------------------------------------------------------------------------
// 测试生命周期
// ---------------------------------------------------------------------------

beforeAll(async () => {
  testWorkspaceRoot = await fs.mkdtemp(nodePath.join(os.tmpdir(), 'sidecar-unit-'));
  testServer = createTestServer(testWorkspaceRoot);

  await new Promise((resolve) => {
    testServer.listen(0, '127.0.0.1', () => {
      testPort = testServer.address().port;
      resolve();
    });
  });
}, 10000);

afterAll(async () => {
  if (testServer) {
    await new Promise((resolve) => testServer.close(resolve));
  }
  if (testWorkspaceRoot) {
    await fs.rm(testWorkspaceRoot, { recursive: true, force: true });
  }
});


// ===========================================================================
// POST /files/write 单元测试
// Validates: Requirements 6.1, 6.5
// ===========================================================================

describe('POST /files/write', () => {

  test('正常写入文件并返回 200', async () => {
    const res = await request('POST', '/files/write', {
      path: 'unit-write/hello.txt',
      content: 'Hello, World!',
    });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    // 验证文件确实写入了
    const filePath = nodePath.join(testWorkspaceRoot, 'unit-write/hello.txt');
    const content = await fs.readFile(filePath, 'utf-8');
    expect(content).toBe('Hello, World!');
  });

  test('写入空字符串内容成功', async () => {
    const res = await request('POST', '/files/write', {
      path: 'unit-write/empty.txt',
      content: '',
    });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    const filePath = nodePath.join(testWorkspaceRoot, 'unit-write/empty.txt');
    const content = await fs.readFile(filePath, 'utf-8');
    expect(content).toBe('');
  });

  test('自动创建父目录', async () => {
    const res = await request('POST', '/files/write', {
      path: 'unit-write/deep/nested/dir/file.json',
      content: '{"key":"value"}',
    });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    const filePath = nodePath.join(testWorkspaceRoot, 'unit-write/deep/nested/dir/file.json');
    const content = await fs.readFile(filePath, 'utf-8');
    expect(content).toBe('{"key":"value"}');
  });

  test('缺少 path 参数返回 400', async () => {
    const res = await request('POST', '/files/write', { content: 'test' });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/path/);
  });

  test('缺少 content 参数返回 400', async () => {
    const res = await request('POST', '/files/write', { path: 'test.txt' });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/content/);
  });

  test('content 为 null 返回 400', async () => {
    const res = await request('POST', '/files/write', { path: 'test.txt', content: null });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  test('路径遍历返回 403', async () => {
    const res = await request('POST', '/files/write', {
      path: '../../etc/passwd',
      content: 'evil',
    });
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/路径越界/);
  });

  test('写入 Unicode 内容成功', async () => {
    const res = await request('POST', '/files/write', {
      path: 'unit-write/unicode.txt',
      content: '你好世界 🌍 こんにちは',
    });
    expect(res.status).toBe(200);

    const filePath = nodePath.join(testWorkspaceRoot, 'unit-write/unicode.txt');
    const content = await fs.readFile(filePath, 'utf-8');
    expect(content).toBe('你好世界 🌍 こんにちは');
  });
});

// ===========================================================================
// POST /files/read 单元测试
// Validates: Requirements 6.2, 6.5, 6.6
// ===========================================================================

describe('POST /files/read', () => {

  beforeAll(async () => {
    // 预先写入测试文件
    const dir = nodePath.join(testWorkspaceRoot, 'unit-read');
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(nodePath.join(dir, 'existing.txt'), 'file content here', 'utf-8');
  });

  test('正常读取文件返回 200 和内容', async () => {
    const res = await request('POST', '/files/read', { path: 'unit-read/existing.txt' });
    expect(res.status).toBe(200);
    expect(res.body.content).toBe('file content here');
  });

  test('文件不存在返回 404', async () => {
    const res = await request('POST', '/files/read', { path: 'unit-read/nonexistent.txt' });
    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/ENOENT/);
  });

  test('缺少 path 参数返回 400', async () => {
    const res = await request('POST', '/files/read', {});
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/path/);
  });

  test('路径遍历返回 403', async () => {
    const res = await request('POST', '/files/read', { path: '../../../etc/passwd' });
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });
});

// ===========================================================================
// POST /files/mkdir 单元测试
// Validates: Requirements 6.3
// ===========================================================================

describe('POST /files/mkdir', () => {

  test('递归创建目录返回 200', async () => {
    const res = await request('POST', '/files/mkdir', { path: 'unit-mkdir/a/b/c' });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    const stat = await fs.stat(nodePath.join(testWorkspaceRoot, 'unit-mkdir/a/b/c'));
    expect(stat.isDirectory()).toBe(true);
  });

  test('已存在目录再次创建返回 200（幂等）', async () => {
    // 先创建
    await request('POST', '/files/mkdir', { path: 'unit-mkdir/idempotent' });
    // 再次创建
    const res = await request('POST', '/files/mkdir', { path: 'unit-mkdir/idempotent' });
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  test('缺少 path 参数返回 400', async () => {
    const res = await request('POST', '/files/mkdir', {});
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/path/);
  });

  test('路径遍历返回 403', async () => {
    const res = await request('POST', '/files/mkdir', { path: '../../evil-dir' });
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });
});

// ===========================================================================
// POST /files/exists 单元测试
// Validates: Requirements 6.4
// ===========================================================================

describe('POST /files/exists', () => {

  beforeAll(async () => {
    const dir = nodePath.join(testWorkspaceRoot, 'unit-exists');
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(nodePath.join(dir, 'afile.txt'), 'content', 'utf-8');
    await fs.mkdir(nodePath.join(dir, 'adir'), { recursive: true });
  });

  test('文件存在返回 exists=true, isFile=true', async () => {
    const res = await request('POST', '/files/exists', { path: 'unit-exists/afile.txt' });
    expect(res.status).toBe(200);
    expect(res.body.exists).toBe(true);
    expect(res.body.isFile).toBe(true);
    expect(res.body.isDirectory).toBe(false);
  });

  test('目录存在返回 exists=true, isDirectory=true', async () => {
    const res = await request('POST', '/files/exists', { path: 'unit-exists/adir' });
    expect(res.status).toBe(200);
    expect(res.body.exists).toBe(true);
    expect(res.body.isFile).toBe(false);
    expect(res.body.isDirectory).toBe(true);
  });

  test('不存在的路径返回 exists=false', async () => {
    const res = await request('POST', '/files/exists', { path: 'unit-exists/nope.txt' });
    expect(res.status).toBe(200);
    expect(res.body.exists).toBe(false);
    expect(res.body.isFile).toBe(false);
    expect(res.body.isDirectory).toBe(false);
  });

  test('缺少 path 参数返回 400', async () => {
    const res = await request('POST', '/files/exists', {});
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/path/);
  });

  test('路径遍历返回 403', async () => {
    const res = await request('POST', '/files/exists', { path: '../../../etc' });
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });
});

// ===========================================================================
// POST /files/list 单元测试
// Validates: Requirements 5.1, 5.2, 5.3
// ===========================================================================

describe('POST /files/list', () => {

  beforeAll(async () => {
    // 构建测试目录结构：
    // unit-list/
    //   src/
    //     utils/
    //       helper.js
    //     index.js
    //   README.md
    const base = nodePath.join(testWorkspaceRoot, 'unit-list');
    await fs.mkdir(nodePath.join(base, 'src', 'utils'), { recursive: true });
    await fs.writeFile(nodePath.join(base, 'src', 'utils', 'helper.js'), 'export {}', 'utf-8');
    await fs.writeFile(nodePath.join(base, 'src', 'index.js'), 'console.log("hi")', 'utf-8');
    await fs.writeFile(nodePath.join(base, 'README.md'), '# Hello', 'utf-8');
  });

  test('返回目录树结构，包含 name、type 和 children', async () => {
    const res = await request('POST', '/files/list', { path: 'unit-list', depth: 3 });
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);

    // 查找 src 目录
    const src = res.body.find(e => e.name === 'src');
    expect(src).toBeDefined();
    expect(src.type).toBe('dir');
    expect(Array.isArray(src.children)).toBe(true);

    // 查找 README.md 文件
    const readme = res.body.find(e => e.name === 'README.md');
    expect(readme).toBeDefined();
    expect(readme.type).toBe('file');
    expect(readme.children).toBeUndefined();

    // 查找嵌套的 utils 目录
    const utils = src.children.find(e => e.name === 'utils');
    expect(utils).toBeDefined();
    expect(utils.type).toBe('dir');
    expect(utils.children.length).toBe(1);
    expect(utils.children[0].name).toBe('helper.js');
    expect(utils.children[0].type).toBe('file');
  });

  test('depth 限制目录递归深度', async () => {
    const res = await request('POST', '/files/list', { path: 'unit-list', depth: 1 });
    expect(res.status).toBe(200);

    const src = res.body.find(e => e.name === 'src');
    expect(src).toBeDefined();
    expect(src.type).toBe('dir');
    // depth=1 时，src 的 children 应为空数组（不递归进入子目录）
    expect(src.children).toEqual([]);
  });

  test('depth 未提供时默认为 3', async () => {
    const res = await request('POST', '/files/list', { path: 'unit-list' });
    expect(res.status).toBe(200);

    // 应能看到 src/utils/helper.js（3 层深度）
    const src = res.body.find(e => e.name === 'src');
    const utils = src.children.find(e => e.name === 'utils');
    expect(utils.children.length).toBe(1);
  });

  test('路径不存在返回 404', async () => {
    const res = await request('POST', '/files/list', { path: 'nonexistent-dir', depth: 3 });
    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/不存在/);
  });

  test('缺少 path 参数返回 400', async () => {
    const res = await request('POST', '/files/list', {});
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/path/);
  });

  test('路径遍历返回 403', async () => {
    const res = await request('POST', '/files/list', { path: '../../etc', depth: 1 });
    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/路径越界/);
  });

  test('指定路径为文件而非目录返回 400', async () => {
    const res = await request('POST', '/files/list', { path: 'unit-list/README.md', depth: 1 });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toMatch(/不是目录/);
  });

  test('空目录返回空数组', async () => {
    await fs.mkdir(nodePath.join(testWorkspaceRoot, 'unit-list-empty'), { recursive: true });
    const res = await request('POST', '/files/list', { path: 'unit-list-empty', depth: 3 });
    expect(res.status).toBe(200);
    expect(res.body).toEqual([]);
  });
});
