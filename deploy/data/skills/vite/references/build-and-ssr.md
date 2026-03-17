---
name: vite-build-ssr
description: Vite library mode and SSR configuration
---

# Build and SSR

## Library Mode

Build a library for distribution:

```ts
// vite.config.ts
import { resolve } from 'node:path'
import { defineConfig } from 'vite'

export default defineConfig({
  build: {
    lib: {
      entry: resolve(import.meta.dirname, 'lib/main.ts'),
      name: 'MyLib',
      fileName: 'my-lib',
    },
    rolldownOptions: {
      external: ['vue', 'react'],
      output: {
        globals: {
          vue: 'Vue',
          react: 'React',
        },
      },
    },
  },
})
```

### Multiple Entries

```ts
build: {
  lib: {
    entry: {
      'my-lib': resolve(import.meta.dirname, 'lib/main.ts'),
      secondary: resolve(import.meta.dirname, 'lib/secondary.ts'),
    },
    name: 'MyLib',
  },
}
```

### Output Formats

- Single entry: `es` and `umd`
- Multiple entries: `es` and `cjs`

### Package.json Setup

```json
{
  "name": "my-lib",
  "type": "module",
  "files": ["dist"],
  "main": "./dist/my-lib.umd.cjs",
  "module": "./dist/my-lib.js",
  "exports": {
    ".": {
      "import": "./dist/my-lib.js",
      "require": "./dist/my-lib.umd.cjs"
    },
    "./style.css": "./dist/my-lib.css"
  }
}
```

## Multi-Page App

```ts
export default defineConfig({
  build: {
    rolldownOptions: {
      input: {
        main: resolve(import.meta.dirname, 'index.html'),
        nested: resolve(import.meta.dirname, 'nested/index.html'),
      },
    },
  },
})
```

## SSR Development

### Middleware Mode

Use Vite as middleware in a custom server:

```ts
import express from 'express'
import { createServer as createViteServer } from 'vite'

const app = express()

const vite = await createViteServer({
  server: { middlewareMode: true },
  appType: 'custom',
})

app.use(vite.middlewares)

app.use('*all', async (req, res, next) => {
  const url = req.originalUrl

  // 1. Read and transform index.html
  let template = await fs.readFile('index.html', 'utf-8')
  template = await vite.transformIndexHtml(url, template)

  // 2. Load server entry
  const { render } = await vite.ssrLoadModule('/src/entry-server.ts')

  // 3. Render app
  const appHtml = await render(url)

  // 4. Inject into template
  const html = template.replace('<!--ssr-outlet-->', appHtml)

  res.status(200).set({ 'Content-Type': 'text/html' }).end(html)
})

app.listen(5173)
```

### SSR Build

```json
{
  "scripts": {
    "build:client": "vite build --outDir dist/client",
    "build:server": "vite build --outDir dist/server --ssr src/entry-server.ts"
  }
}
```

The `--ssr` flag:
- Externalizes dependencies by default
- Outputs for Node.js consumption

### SSR Manifest

Generate asset mapping for preload hints:

```bash
vite build --outDir dist/client --ssrManifest
```

Creates `dist/client/.vite/ssr-manifest.json` mapping module IDs to chunks.

### SSR Externals

Control which deps get bundled vs externalized:

```ts
export default defineConfig({
  ssr: {
    noExternal: ['some-package'],  // Bundle this dep
    external: ['another-package'], // Externalize this dep
  },
})
```

### Conditional Logic

```ts
if (import.meta.env.SSR) {
  // Server-only code (tree-shaken from client)
}
```

## JavaScript API

### createServer

```ts
import { createServer } from 'vite'

const server = await createServer({
  configFile: false,
  root: import.meta.dirname,
  server: { port: 1337 },
})

await server.listen()
server.printUrls()
```

### build

```ts
import { build } from 'vite'

await build({
  root: './project',
  build: { outDir: 'dist' },
})
```

### preview

```ts
import { preview } from 'vite'

const previewServer = await preview({
  preview: { port: 8080, open: true },
})
previewServer.printUrls()
```

### resolveConfig

```ts
import { resolveConfig } from 'vite'

const config = await resolveConfig({}, 'build')
```

### loadEnv

```ts
import { loadEnv } from 'vite'

const env = loadEnv('development', process.cwd(), '')
// Loads all env vars (empty prefix = no filtering)
```

<!--
Source references:
- https://vite.dev/guide/build
- https://vite.dev/guide/ssr
- https://vite.dev/guide/api-javascript
-->
