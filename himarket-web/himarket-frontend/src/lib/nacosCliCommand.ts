const DEFAULT_NACOS_HOST = 'market.hiclaw.io';

export interface NacosCliServerInfo {
  nacosHost: string;
  nacosPort?: number;
}

export interface NacosCliCommandParams {
  command: 'agentspec-get' | 'skill-get';
  outputDir?: string;
  resourceName: string;
  server: NacosCliServerInfo;
  version?: string;
}

function quoteIfNeeded(value: string): string {
  return value.includes(' ') ? `"${value}"` : value;
}

export function buildNacosCliCommand(params: NacosCliCommandParams): string {
  const isDefaultHost = params.server.nacosHost === DEFAULT_NACOS_HOST;
  const hostArg = isDefaultHost ? '' : ` --host ${params.server.nacosHost}`;
  const portArg = params.server.nacosPort ? ` --port ${params.server.nacosPort}` : '';
  const outputArg = params.outputDir ? ` -o ${quoteIfNeeded(params.outputDir)}` : '';
  const versionArg = params.version ? ` --version ${params.version}` : '';

  return `npx @nacos-group/cli${hostArg}${portArg} ${params.command} ${quoteIfNeeded(params.resourceName)}${outputArg}${versionArg}`;
}
