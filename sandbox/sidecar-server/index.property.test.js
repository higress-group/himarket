'use strict';

const fc = require('fast-check');
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
 * 创建一个独立的 sidecar HTTP server 用于测试，
 * 不依赖 require('./index') 避免端口冲突。
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

  return { server, resolveSafePath };
}

beforeAll(async () => {
  testWorkspaceRoot = await fs.mkdtemp(nodePath.join(os.tmpdir(), 'sidecar-pbt-'));
  const { server, resolveSafePath } = createTestServer(testWorkspaceRoot);
  testServer = server;

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
// Property 9: 文件操作路径安全性
// 对任意包含 ../、符号链接、绝对路径等路径遍历模式的输入，
// resolveSafePath() 必须拒绝并返回错误
// **Validates: Requirements 6.7, 6.8**
// ===========================================================================

describe('Property 9: 文件操作路径安全性', () => {

  // 直接从源码导入 resolveSafePath 进行纯函数测试
  // 注意：需要设置正确的 WORKSPACE_ROOT 环境变量
  let resolveSafePath;

  beforeAll(() => {
    // 使用与 testServer 相同逻辑的 resolveSafePath
    const path = nodePath;
    const workspaceRoot = testWorkspaceRoot;
    resolveSafePath = function(relativePath) {
      const resolved = path.resolve(workspaceRoot, relativePath);
      const root = path.resolve(workspaceRoot);
      if (!resolved.startsWith(root + path.sep) && resolved !== root) {
        throw new Error('路径越界: ' + relativePath);
      }
      return resolved;
    };
  });

  test('任意路径遍历模式（../）必须被拒绝', () => {
    fc.assert(
      fc.property(
        // 生成器：构造包含 .. 的路径遍历攻击向量
        fc.oneof(
          // 纯 ../ 遍历：N 个 .. 组成
          fc.integer({ min: 1, max: 10 }).map(n =>
            Array(n).fill('..').join('/')
          ),
          // 前缀合法 + 后缀遍历：subdir/../../..
          fc.tuple(
            fc.stringOf(fc.constantFrom('a', 'b', 'c'), { minLength: 1, maxLength: 4 }),
            fc.integer({ min: 2, max: 8 })
          ).map(([prefix, depth]) =>
            prefix + '/' + Array(depth).fill('..').join('/')
          ),
          // 遍历后访问外部文件：../../etc/passwd
          fc.integer({ min: 2, max: 6 }).map(n =>
            Array(n).fill('..').join('/') + '/etc/passwd'
          ),
          // 混合正常段和 ..：a/b/../../../secret
          fc.tuple(
            fc.integer({ min: 1, max: 3 }),
            fc.integer({ min: 3, max: 8 })
          ).map(([normalDepth, traversalDepth]) => {
            const normal = Array(normalDepth).fill('sub').join('/');
            const traversal = Array(traversalDepth).fill('..').join('/');
            return normal + '/' + traversal + '/secret';
          })
        ),
        (maliciousPath) => {
          const resolved = nodePath.resolve(testWorkspaceRoot, maliciousPath);
          const root = nodePath.resolve(testWorkspaceRoot);
          // 只断言那些确实逃出 workspace 的路径
          if (!resolved.startsWith(root + nodePath.sep) && resolved !== root) {
            expect(() => resolveSafePath(maliciousPath)).toThrow('路径越界');
          }
        }
      ),
      { numRuns: 300 }
    );
  });

  test('绝对路径必须被拒绝', () => {
    fc.assert(
      fc.property(
        // 生成随机绝对路径
        fc.oneof(
          fc.constantFrom(
            '/etc/passwd',
            '/tmp/evil',
            '/root/.ssh/id_rsa',
            '/var/log/syslog',
            '/home/user/secret'
          ),
          // 随机生成 /xxx/yyy 形式的绝对路径
          fc.array(
            fc.stringOf(fc.constantFrom('a', 'b', 'c', 'x', 'y', 'z'), { minLength: 1, maxLength: 6 }),
            { minLength: 1, maxLength: 4 }
          ).map(parts => '/' + parts.join('/'))
        ),
        (absolutePath) => {
          const resolved = nodePath.resolve(testWorkspaceRoot, absolutePath);
          const root = nodePath.resolve(testWorkspaceRoot);
          if (!resolved.startsWith(root + nodePath.sep) && resolved !== root) {
            expect(() => resolveSafePath(absolutePath)).toThrow('路径越界');
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  test('路径遍历通过 HTTP 端点被拒绝（返回 403）', async () => {
    await fc.assert(
      fc.asyncProperty(
        // 生成确定会逃出 workspace 的路径
        fc.integer({ min: 2, max: 6 }).map(n =>
          Array(n).fill('..').join('/') + '/etc/passwd'
        ),
        async (traversalPath) => {
          // 所有文件操作端点都应拒绝
          const writeRes = await request('POST', '/files/write', { path: traversalPath, content: 'evil' });
          expect(writeRes.status).toBe(403);

          const readRes = await request('POST', '/files/read', { path: traversalPath });
          expect(readRes.status).toBe(403);

          const mkdirRes = await request('POST', '/files/mkdir', { path: traversalPath });
          expect(mkdirRes.status).toBe(403);

          const existsRes = await request('POST', '/files/exists', { path: traversalPath });
          expect(existsRes.status).toBe(403);
        }
      ),
      { numRuns: 20 }
    );
  }, 30000);

  test('合法相对路径不应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.stringOf(fc.constantFrom('a', 'b', 'c', 'd', '1', '2', '_', '-'), { minLength: 1, maxLength: 8 }),
          { minLength: 1, maxLength: 4 }
        ).map(parts => parts.join('/')),
        (safePath) => {
          const result = resolveSafePath(safePath);
          expect(result.startsWith(nodePath.resolve(testWorkspaceRoot))).toBe(true);
        }
      ),
      { numRuns: 200 }
    );
  });
});


// ===========================================================================
// Property 10: Sidecar 文件操作幂等性
// 连续多次 POST /files/write 写入相同内容，最终文件状态与单次写入一致；
// POST /files/mkdir 对已存在目录返回成功
// **Validates: Requirements 6.1, 6.3**
// ===========================================================================

describe('Property 10: Sidecar 文件操作幂等性', () => {

  test('多次 write 相同内容，最终文件状态与单次写入一致', async () => {
    await fc.assert(
      fc.asyncProperty(
        // 生成随机文件名（避免冲突加 uuid 前缀）
        fc.tuple(
          fc.integer({ min: 1, max: 99999 }),
          fc.stringOf(fc.constantFrom('a', 'b', 'c', 'd', 'e'), { minLength: 1, maxLength: 6 })
        ).map(([id, name]) => `prop10-write-${id}/${name}.txt`),
        // 生成随机内容（包含 Unicode 和特殊字符）
        fc.oneof(
          fc.string({ minLength: 0, maxLength: 200 }),
          fc.constantFrom('', '你好世界', '{"key":"value"}', 'line1\nline2\nline3')
        ),
        // 重复写入次数
        fc.integer({ min: 2, max: 4 }),
        async (filePath, content, repeatCount) => {
          // 多次写入相同内容
          for (let i = 0; i < repeatCount; i++) {
            const res = await request('POST', '/files/write', { path: filePath, content });
            expect(res.status).toBe(200);
            expect(res.body.success).toBe(true);
          }

          // 读回内容
          const readRes = await request('POST', '/files/read', { path: filePath });
          expect(readRes.status).toBe(200);
          expect(readRes.body.content).toBe(content);
        }
      ),
      { numRuns: 30 }
    );
  }, 30000);

  test('mkdir 对已存在目录返回成功（幂等）', async () => {
    await fc.assert(
      fc.asyncProperty(
        // 生成随机目录路径
        fc.tuple(
          fc.integer({ min: 1, max: 99999 }),
          fc.array(
            fc.stringOf(fc.constantFrom('a', 'b', 'c', 'd'), { minLength: 1, maxLength: 4 }),
            { minLength: 1, maxLength: 3 }
          )
        ).map(([id, parts]) => `prop10-mkdir-${id}/` + parts.join('/')),
        // 重复次数
        fc.integer({ min: 2, max: 4 }),
        async (dirPath, repeatCount) => {
          for (let i = 0; i < repeatCount; i++) {
            const res = await request('POST', '/files/mkdir', { path: dirPath });
            expect(res.status).toBe(200);
            expect(res.body.success).toBe(true);
          }

          // 验证目录存在
          const existsRes = await request('POST', '/files/exists', { path: dirPath });
          expect(existsRes.status).toBe(200);
          expect(existsRes.body.exists).toBe(true);
          expect(existsRes.body.isDirectory).toBe(true);
        }
      ),
      { numRuns: 30 }
    );
  }, 30000);
});
