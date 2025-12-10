// import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig } from 'eslint/config'

export default defineConfig(
  tseslint.configs.recommended,
  reactHooks.configs['recommended-latest'],
  {
    extends: [
      reactRefresh.configs.vite
    ]
  },
  {
    languageOptions: {
      globals: globals.browser,
    }
  }
)

