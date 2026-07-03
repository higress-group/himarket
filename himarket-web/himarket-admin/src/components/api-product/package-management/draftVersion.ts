function parseSemverVersion(version: string) {
  const match = version.trim().match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!match) return null;
  return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]) };
}

function parseLegacyVersion(version: string) {
  const match = version.trim().match(/^[vV](\d+)$/);
  if (!match) return null;
  const parsed = Number(match[1]);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export function suggestNextVersionFromBase(baseVersion: string) {
  const semver = parseSemverVersion(baseVersion);
  if (semver) {
    return `${semver.major}.${semver.minor}.${semver.patch + 1}`;
  }
  const legacy = parseLegacyVersion(baseVersion);
  if (legacy !== null) {
    return `v${legacy + 1}`;
  }
  return baseVersion;
}

export function compareDraftVersion(targetVersion: string, baseVersion: string) {
  const targetSemver = parseSemverVersion(targetVersion);
  const baseSemver = parseSemverVersion(baseVersion);
  if (targetSemver && baseSemver) {
    if (targetSemver.major !== baseSemver.major) {
      return targetSemver.major - baseSemver.major;
    }
    if (targetSemver.minor !== baseSemver.minor) {
      return targetSemver.minor - baseSemver.minor;
    }
    return targetSemver.patch - baseSemver.patch;
  }

  const targetLegacy = parseLegacyVersion(targetVersion);
  const baseLegacy = parseLegacyVersion(baseVersion);
  if (targetLegacy !== null && baseLegacy !== null) {
    return targetLegacy - baseLegacy;
  }
  return null;
}

export function isSupportedDraftVersion(version: string) {
  return parseSemverVersion(version) !== null || parseLegacyVersion(version) !== null;
}
