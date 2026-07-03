import type { AgentSpecCard, AgentSpecResource } from '@/types/api-product';

export function cloneAgentSpecCard(agentSpecCard: AgentSpecCard): AgentSpecCard {
  return JSON.parse(JSON.stringify(agentSpecCard)) as AgentSpecCard;
}

function getAgentSpecResourcePath(resource: AgentSpecResource, agentSpecName?: string) {
  const name = resource.name || '';
  let path = resource.type ? `${resource.type}/${name}` : name;
  const specNamePrefix = agentSpecName ? `${agentSpecName}/` : '';
  if (specNamePrefix && path.startsWith(specNamePrefix)) {
    path = path.substring(specNamePrefix.length);
  }
  return path;
}

export function findAgentSpecResourceKey(agentSpecCard: AgentSpecCard, path: string) {
  return Object.entries(agentSpecCard.resource ?? {}).find(
    ([, resource]) => getAgentSpecResourcePath(resource, agentSpecCard.name) === path,
  )?.[0];
}

export function getAgentSpecCardFileContent(agentSpecCard: AgentSpecCard, path: string) {
  if (path === 'manifest.json') {
    return agentSpecCard.content;
  }

  const resourceKey = findAgentSpecResourceKey(agentSpecCard, path);
  return resourceKey ? agentSpecCard.resource?.[resourceKey]?.content : undefined;
}
