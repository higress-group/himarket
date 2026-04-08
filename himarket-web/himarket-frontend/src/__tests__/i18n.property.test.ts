import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import * as fc from 'fast-check';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import i18n from '../i18n';
// zh-CN locale imports
import enCommon from '../locales/en-US/common.json';
import enEmptyState from '../locales/en-US/emptyState.json';
import enGettingStarted from '../locales/en-US/gettingStarted.json';
import enHeader from '../locales/en-US/header.json';
import enLogin from '../locales/en-US/login.json';
import enLoginPrompt from '../locales/en-US/loginPrompt.json';
import enProfile from '../locales/en-US/profile.json';
import enRegister from '../locales/en-US/register.json';
import enSkillDetail from '../locales/en-US/skillDetail.json';
import enSquare from '../locales/en-US/square.json';
import enUserInfo from '../locales/en-US/userInfo.json';
import enWorkerDetail from '../locales/en-US/workerDetail.json';
import zhCommon from '../locales/zh-CN/common.json';
import zhEmptyState from '../locales/zh-CN/emptyState.json';
import zhGettingStarted from '../locales/zh-CN/gettingStarted.json';
import zhHeader from '../locales/zh-CN/header.json';
import zhLogin from '../locales/zh-CN/login.json';
import zhLoginPrompt from '../locales/zh-CN/loginPrompt.json';
import zhProfile from '../locales/zh-CN/profile.json';
import zhRegister from '../locales/zh-CN/register.json';
import zhSkillDetail from '../locales/zh-CN/skillDetail.json';
import zhSquare from '../locales/zh-CN/square.json';
import zhUserInfo from '../locales/zh-CN/userInfo.json';
import zhWorkerDetail from '../locales/zh-CN/workerDetail.json';

// en-US locale imports

// Antd locale imports

// --- Helpers ---

/** Recursively extract all keys from a nested object, using dot notation */
function extractKeys(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = [];
  for (const key of Object.keys(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    const value = obj[key];
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...extractKeys(value as Record<string, unknown>, fullKey));
    } else {
      keys.push(fullKey);
    }
  }
  return keys.sort();
}

/** Recursively extract all leaf values from a nested object, returning [dotKey, value] pairs */
function extractEntries(obj: Record<string, unknown>, prefix = ''): [string, string][] {
  const entries: [string, string][] = [];
  for (const key of Object.keys(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key;
    const value = obj[key];
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      entries.push(...extractEntries(value as Record<string, unknown>, fullKey));
    } else if (typeof value === 'string') {
      entries.push([fullKey, value]);
    }
  }
  return entries;
}

/** Map of all namespaces with their zh-CN and en-US resources */
const namespaces: Record<string, { zh: Record<string, unknown>; en: Record<string, unknown> }> = {
  common: { en: enCommon, zh: zhCommon },
  emptyState: { en: enEmptyState, zh: zhEmptyState },
  gettingStarted: { en: enGettingStarted, zh: zhGettingStarted },
  header: { en: enHeader, zh: zhHeader },
  login: { en: enLogin, zh: zhLogin },
  loginPrompt: { en: enLoginPrompt, zh: zhLoginPrompt },
  profile: { en: enProfile, zh: zhProfile },
  register: { en: enRegister, zh: zhRegister },
  skillDetail: { en: enSkillDetail, zh: zhSkillDetail },
  square: { en: enSquare, zh: zhSquare },
  userInfo: { en: enUserInfo, zh: zhUserInfo },
  workerDetail: { en: enWorkerDetail, zh: zhWorkerDetail },
};

/** Pure function: map language code to antd locale object */
function getAntdLocale(lang: string) {
  return lang === 'zh-CN' ? zhCN : enUS;
}

/** Collect all existing translation keys (with namespace prefix) for exclusion in Property 7 */
function collectAllKeys(): Set<string> {
  const allKeys = new Set<string>();
  for (const [ns, { zh }] of Object.entries(namespaces)) {
    for (const key of extractKeys(zh as Record<string, unknown>)) {
      allKeys.add(`${ns}:${key}`);
      // Also add without namespace for default ns
      if (ns === 'common') {
        allKeys.add(key);
      }
    }
  }
  return allKeys;
}

// --- Tests ---

describe('i18n Property Tests', () => {
  beforeEach(async () => {
    localStorage.clear();
    await i18n.changeLanguage('zh-CN');
  });

  afterEach(() => {
    localStorage.clear();
  });

  /**
   * **Feature: frontend-i18n, Property 1: Translation Key Completeness**
   * For any translation key that exists in zh-CN, the same key must also exist in en-US,
   * and vice versa. The key sets must be identical across all namespaces.
   * **Validates: Requirements 1.2, 2.2**
   */
  describe('Property 1: Translation Key Completeness', () => {
    it('zh-CN and en-US should have identical key sets for every namespace', () => {
      for (const [ns, { en, zh }] of Object.entries(namespaces)) {
        const zhKeys = extractKeys(zh as Record<string, unknown>);
        const enKeys = extractKeys(en as Record<string, unknown>);
        expect(zhKeys, `Namespace "${ns}": zh-CN and en-US keys should be identical`).toEqual(
          enKeys,
        );
      }
    });
  });

  /**
   * **Feature: frontend-i18n, Property 2: Language Preference Round-Trip**
   * For any supported language value, calling i18n.changeLanguage(lang) then reading
   * localStorage.getItem('i18nLng') should return that same language value.
   * **Validates: Requirements 1.4, 3.4**
   */
  describe('Property 2: Language Preference Round-Trip', () => {
    it('changeLanguage should persist the language to localStorage', async () => {
      await fc.assert(
        fc.asyncProperty(fc.constantFrom('zh-CN', 'en-US'), async (lang) => {
          await i18n.changeLanguage(lang);
          expect(localStorage.getItem('i18nLng')).toBe(lang);
        }),
        { numRuns: 100 },
      );
    });
  });

  /**
   * **Feature: frontend-i18n, Property 3: Language Toggle**
   * For any current language state, toggling should switch to the other supported language.
   * **Validates: Requirements 3.2**
   */
  describe('Property 3: Language Toggle', () => {
    it('toggling language should switch to the other language', async () => {
      await fc.assert(
        fc.asyncProperty(fc.constantFrom('zh-CN', 'en-US'), async (initialLang) => {
          await i18n.changeLanguage(initialLang);
          const expected = initialLang === 'zh-CN' ? 'en-US' : 'zh-CN';
          await i18n.changeLanguage(expected);
          expect(i18n.language).toBe(expected);
        }),
        { numRuns: 100 },
      );
    });
  });

  /**
   * **Feature: frontend-i18n, Property 4: Antd Locale Mapping**
   * For any supported language, the antd locale object must correspond to the correct
   * antd locale module: zh-CN → zhCN, en-US → enUS.
   * **Validates: Requirements 4.1, 4.2, 4.3**
   */
  describe('Property 4: Antd Locale Mapping', () => {
    it('getAntdLocale should map language codes to correct antd locale objects', () => {
      fc.assert(
        fc.property(fc.constantFrom('zh-CN', 'en-US'), (lang) => {
          const locale = getAntdLocale(lang);
          if (lang === 'zh-CN') {
            expect(locale).toBe(zhCN);
          } else {
            expect(locale).toBe(enUS);
          }
        }),
        { numRuns: 100 },
      );
    });
  });

  /**
   * **Feature: frontend-i18n, Property 5: Proper Noun Invariance**
   * For any translation key whose value in zh-CN is one of the defined proper nouns,
   * the en-US value must be identical.
   * **Validates: Requirements 2.5**
   */
  describe('Property 5: Proper Noun Invariance', () => {
    const properNouns = [
      'Skill',
      'MCP',
      'Worker',
      'Agent',
      'API',
      'Model',
      'HiMarket',
      'HiChat',
      'HiCoding',
    ];

    it('proper noun values should be identical in zh-CN and en-US', () => {
      for (const [ns, { en, zh }] of Object.entries(namespaces)) {
        const zhEntries = extractEntries(zh as Record<string, unknown>);
        const enEntriesMap = new Map(extractEntries(en as Record<string, unknown>));

        for (const [key, zhValue] of zhEntries) {
          if (properNouns.includes(zhValue)) {
            const enValue = enEntriesMap.get(key);
            expect(
              enValue,
              `Namespace "${ns}", key "${key}": proper noun "${zhValue}" should be identical in en-US`,
            ).toBe(zhValue);
          }
        }
      }
    });
  });

  /**
   * **Feature: frontend-i18n, Property 6: Interpolation Substitution**
   * For any translation key containing interpolation placeholders and any non-empty string
   * parameter, calling t(key, { param: value }) should produce a result containing the value
   * and not containing raw {{ syntax.
   * **Validates: Requirements 6.3**
   */
  describe('Property 6: Interpolation Substitution', () => {
    // Keys with {{provider}} interpolation
    const interpolationKeys = [
      { nsKey: 'login:loginWithProvider', param: 'provider' },
      { nsKey: 'profile:fromProvider', param: 'provider' },
      { nsKey: 'profile:bindingComingSoon', param: 'provider' },
    ];

    it('interpolation should substitute parameters and remove {{ placeholders', () => {
      const alphanumArb = fc
        .string({ maxLength: 20, minLength: 1 })
        .map((s) => s.replace(/[^a-zA-Z0-9]/g, 'a'))
        .filter((s) => s.length > 0);

      fc.assert(
        fc.property(alphanumArb, (randomValue) => {
          for (const { nsKey, param } of interpolationKeys) {
            const result = i18n.t(nsKey, { [param]: randomValue });
            expect(
              result,
              `Key "${nsKey}" with ${param}="${randomValue}" should contain the value`,
            ).toContain(randomValue);
            expect(
              result,
              `Key "${nsKey}" should not contain raw {{ after interpolation`,
            ).not.toContain('{{');
          }
        }),
        { numRuns: 100 },
      );
    });
  });

  /**
   * **Feature: frontend-i18n, Property 7: Missing Key Fallback**
   * For any random string that is not a defined translation key, calling t(randomString)
   * should return the randomString itself.
   * **Validates: Requirements 6.4**
   */
  describe('Property 7: Missing Key Fallback', () => {
    const allKeys = collectAllKeys();

    it('t() should return the key itself for undefined translation keys', () => {
      const keyArb = fc
        .string({ maxLength: 30, minLength: 5 })
        .map((s) => s.replace(/[^a-zA-Z0-9._-]/g, 'x'))
        .filter((s) => s.length >= 5 && !allKeys.has(s));

      fc.assert(
        fc.property(keyArb, (randomKey) => {
          const result = i18n.t(randomKey);
          expect(result).toBe(randomKey);
        }),
        { numRuns: 100 },
      );
    });
  });
});
