import { describe, expect, it } from 'vitest';

import { buildNacosCliCommand } from '../nacosCliCommand';

describe('buildNacosCliCommand', () => {
  it('includes host and port for skill download commands', () => {
    const command = buildNacosCliCommand({
      command: 'skill-get',
      outputDir: './my skills',
      resourceName: 'demo skill',
      server: {
        nacosHost: 'nacos.himarket.hosecloud.com',
        nacosPort: 80,
      },
      version: '1.0.0',
    });

    expect(command).toBe(
      'npx @nacos-group/cli --host nacos.himarket.hosecloud.com --port 80 skill-get "demo skill" -o "./my skills" --version 1.0.0',
    );
  });

  it('keeps port when the host is the default nacos host', () => {
    const command = buildNacosCliCommand({
      command: 'agentspec-get',
      resourceName: 'agent-demo',
      server: {
        nacosHost: 'market.hiclaw.io',
        nacosPort: 8848,
      },
    });

    expect(command).toBe('npx @nacos-group/cli --port 8848 agentspec-get agent-demo');
  });
});
