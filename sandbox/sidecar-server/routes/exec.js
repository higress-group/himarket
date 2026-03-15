import { execFile } from 'node:child_process';
import { parseJsonBody, sendJson } from '../lib/http.js';
import { resolvePath } from '../lib/path.js';
import { WORKSPACE_ROOT } from '../config.js';

// ---------------------------------------------------------------------------
// POST /exec — 在沙箱内执行命令并返回结果
// ---------------------------------------------------------------------------

export const execRoutes = [
  {
    method: 'POST',
    match: (url) => url === '/exec',
    handler: async (req, res) => {
      let body;
      try {
        body = await parseJsonBody(req);
      } catch {
        return sendJson(res, 400, { error: '无效的 JSON 请求体' });
      }
      const {
        command,
        args: cmdArgs,
        cwd: execCwd,
        timeout: execTimeout,
      } = body;
      if (!command) {
        return sendJson(res, 400, { error: '缺少 command 参数' });
      }
      const spawnArgs = Array.isArray(cmdArgs) ? cmdArgs : [];
      const spawnCwd = execCwd ? resolvePath(execCwd) : WORKSPACE_ROOT;
      const timeoutMs =
        typeof execTimeout === 'number' && execTimeout > 0
          ? execTimeout
          : 120000;

      try {
        const result = await new Promise((resolve) => {
          execFile(
            command,
            spawnArgs,
            {
              cwd: spawnCwd,
              timeout: timeoutMs,
              maxBuffer: 10 * 1024 * 1024,
              env: { ...process.env },
            },
            (err, stdout, stderr) => {
              if (err && err.killed) {
                // 超时被 kill
                resolve({
                  exitCode: 124,
                  stdout: stdout || '',
                  stderr: (stderr || '') + '\nProcess timed out',
                });
              } else if (err && err.code === 'ENOENT') {
                resolve({
                  exitCode: 127,
                  stdout: '',
                  stderr: `Command not found: ${command}`,
                });
              } else if (err) {
                resolve({
                  exitCode: err.code || 1,
                  stdout: stdout || '',
                  stderr: stderr || err.message,
                });
              } else {
                resolve({
                  exitCode: 0,
                  stdout: stdout || '',
                  stderr: stderr || '',
                });
              }
            },
          );
        });
        sendJson(res, 200, result);
      } catch (err) {
        sendJson(res, 500, { error: err.message });
      }
    },
  },
];
