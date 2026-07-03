import type { SkillCard, SkillResource } from '@/types/api-product';

function normalizeSkillFilePath(path: string, skillName?: string) {
  const normalizedPath = path.replace(/^\/+/, '');
  const skillNamePrefix = skillName ? `${skillName}/` : '';
  if (skillNamePrefix && normalizedPath.startsWith(skillNamePrefix)) {
    return normalizedPath.substring(skillNamePrefix.length);
  }
  return normalizedPath;
}

function getSkillResourcePath(key: string, resource: SkillResource, skillName?: string) {
  const resourceName = resource.name || key;
  const resourcePath =
    resource.type && !resourceName.startsWith(`${resource.type}/`)
      ? `${resource.type}/${resourceName}`
      : resourceName;
  return normalizeSkillFilePath(resourcePath, skillName);
}

export function cloneSkillCard(skillCard: SkillCard): SkillCard {
  return JSON.parse(JSON.stringify(skillCard)) as SkillCard;
}

export function findSkillResourceKey(skillCard: SkillCard, path: string) {
  const normalizedPath = normalizeSkillFilePath(path, skillCard.name);
  return Object.entries(skillCard.resource ?? {}).find(
    ([key, resource]) => getSkillResourcePath(key, resource, skillCard.name) === normalizedPath,
  )?.[0];
}

export function getSkillCardFileContent(skillCard: SkillCard, path: string) {
  if (path === 'SKILL.md') {
    return skillCard.skillMd;
  }

  const resourceKey = findSkillResourceKey(skillCard, path);
  return resourceKey ? skillCard.resource?.[resourceKey]?.content : undefined;
}
