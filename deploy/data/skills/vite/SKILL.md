---
name: vite
description: Build web applications with Vite. Use when user asks to create, build, or scaffold a web app, website, page, or frontend project. Triggers on requests like "build a todo app", "create a web page", "make a dashboard", etc.
metadata:
  author: Anthony Fu
  version: "2026.1.31"
  source: Generated from https://github.com/vitejs/vite, scripts at https://github.com/antfu/skills
---

# Vite Web App Builder

> Based on Vite 8 beta (Rolldown-powered). Vite 8 uses Rolldown bundler and Oxc transformer.

This skill builds complete web applications using Vite. When a user requests a web app (e.g. "build a todo app"), follow the workflow below to scaffold, develop, and launch it.

## Defaults

- **Framework:** React + TypeScript
- **Styling:** Tailwind CSS v4
- **Dev server port:** 3000
- **Project location:** Current working directory (create a named subfolder)
- **Language:** TypeScript (strict mode)
- **Module system:** ESM only, no CommonJS

## Workflow

### Step 1: Scaffold the project

Create the project in a subfolder of the current working directory. Derive the folder name from the user's request (e.g. "todo app" → `todo-app`).

```bash
npm create vite@latest <project-name> -- --template react-ts
cd <project-name>
npm install
```

### Step 2: Install Tailwind CSS v4

```bash
npm install tailwindcss @tailwindcss/vite
```

Update `vite.config.ts`:

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { '@': '/src' } },
  server: { port: 3000 },
  build: { target: 'esnext', outDir: 'dist' },
})
```

Replace the content of `src/index.css` with:

```css
@import "tailwindcss";
```

### Step 3: Implement the application

- Build the complete app the user requested in `src/`
- Use functional React components with hooks
- Use Tailwind CSS utility classes for all styling
- Keep all code in TypeScript (`.tsx` / `.ts`)
- Use `@/` path alias for imports from `src/`
- Produce clean, working code — no placeholder or TODO comments
- Make the UI visually polished: responsive layout, appropriate spacing, good color choices
- Include proper error states and loading states where applicable

### Step 4: Start the dev server and preview

```bash
npm run dev -- --port 3000
```

After starting, use `run_preview` to open the browser preview at `http://localhost:3000` so the user can see the result.

## Important Rules

1. **Always produce a fully working app.** Never leave stubs or unfinished pieces.
2. **Do not ask the user which framework/styling to use** unless they explicitly mention a different preference. Use the defaults above.
3. **Clean up scaffolding boilerplate.** Remove the default Vite welcome page content (`App.tsx`, `App.css`, etc.) and replace with the actual application.
4. **Single-page apps only** unless the user explicitly asks for routing.
5. **No unnecessary dependencies.** Only add packages that are truly needed for the requested functionality.
6. **Port 3000 is mandatory.** If port 3000 is occupied, report to the user rather than silently using another port.

## Reference Materials

### Vite Configuration

| Topic | Description | Reference |
|-------|-------------|-----------|
| Configuration | `vite.config.ts`, `defineConfig`, conditional configs, `loadEnv` | [core-config](references/core-config.md) |
| Features | `import.meta.glob`, asset queries (`?raw`, `?url`), `import.meta.env`, HMR API | [core-features](references/core-features.md) |
| Plugin API | Vite-specific hooks, virtual modules, plugin ordering | [core-plugin-api](references/core-plugin-api.md) |
| Build & SSR | Library mode, SSR middleware mode, `ssrLoadModule`, JavaScript API | [build-and-ssr](references/build-and-ssr.md) |
| Environment API | Vite 6+ multi-environment support, custom runtimes | [environment-api](references/environment-api.md) |
| Rolldown Migration | Vite 8 changes: Rolldown bundler, Oxc transformer, config migration | [rolldown-migration](references/rolldown-migration.md) |

### CLI Commands

```bash
vite              # Start dev server
vite build        # Production build
vite preview      # Preview production build
```

### Official Plugins

- `@vitejs/plugin-react` - React with Oxc/Babel (default)
- `@vitejs/plugin-react-swc` - React with SWC (alternative)
- `@vitejs/plugin-vue` - Vue 3 SFC support
- `@vitejs/plugin-vue-jsx` - Vue 3 JSX
- `@vitejs/plugin-legacy` - Legacy browser support
