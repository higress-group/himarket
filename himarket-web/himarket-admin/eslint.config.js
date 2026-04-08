import importPlugin from 'eslint-plugin-import';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import perfectionist from 'eslint-plugin-perfectionist';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  // 忽略的文件和目录
  {
    ignores: ['node_modules/', 'build/', 'dist/', '**/*.min.js', '**/*-min.js', '**/*.bundle.js'],
  },
  ...tseslint.configs.recommended,
  eslintPluginPrettierRecommended,
  {
    files: ['**/*.{js,jsx,ts,tsx}'],
    languageOptions: {
      globals: globals.browser,
    },
    plugins: {
      import: importPlugin,
      'jsx-a11y': jsxA11y,
      perfectionist,
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      // React Hooks 规则
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/consistent-type-imports': 'error',
      // TypeScript 严格规则
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',

      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      eqeqeq: ['error', 'always'],
      // 导入规则
      'import/order': [
        'error',
        {
          alphabetize: { caseInsensitive: true, order: 'asc' },
          groups: [
            'builtin',
            'external',
            'internal',
            ['parent', 'sibling'],
            'index',
            'object',
            'type',
          ],
          'newlines-between': 'always',
          pathGroups: [{ group: 'internal', pattern: '@/**' }],
        },
      ],

      // JSX 无障碍访问
      'jsx-a11y/alt-text': 'error',
      'jsx-a11y/anchor-is-valid': 'error',
      'jsx-a11y/click-events-have-key-events': 'error',
      'jsx-a11y/no-static-element-interactions': 'error',

      // 代码质量规则
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'perfectionist/sort-jsx-props': ['error', { order: 'asc', type: 'alphabetical' }],
      // Perfectionist 规则
      'perfectionist/sort-objects': ['error', { order: 'asc', type: 'alphabetical' }],
      'prefer-const': 'error',

      'react-hooks/exhaustive-deps': 'error',
      // 允许在 effect 中调用异步函数，只要状态更新在异步回调中
      'react-hooks/set-state-in-effect': 'off',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],

      // React 规则
      'react/jsx-filename-extension': ['error', { extensions: ['.tsx', '.jsx'] }],

      'react/no-unescaped-entities': 'error',
      'react/react-in-jsx-scope': 'off',
    },
    settings: {
      react: { version: 'detect' },
    },
  },
);
