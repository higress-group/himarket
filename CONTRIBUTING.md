# Contributing to Himarket

Thank you for your interest in contributing to Himarket! 🎉

We welcome contributions of all kinds: bug fixes, new features, documentation improvements, and more.

## 🚀 Getting Started

### Fork and Clone the Repository

1. **Fork** the [Himarket repository](https://github.com/higress-group/himarket) to your GitHub account
2. **Clone** your fork to your local machine:

```bash
git clone https://github.com/YOUR_USERNAME/himarket.git
cd himarket
```

3. **Add the upstream repository** so you can keep your fork in sync:

```bash
git remote add upstream https://github.com/higress-group/himarket.git
```

4. **Set up your development environment** by following the instructions in [README.md](README.md)

---

## 🔄 Development Workflow

### 1. Sync and Create a Branch

Before starting work, sync your fork with the upstream repository:

```bash
# Switch to main branch
git checkout main

# Pull latest changes from upstream
git pull upstream main

# Push updates to your fork
git push origin main

# Create a new feature branch
git checkout -b feat/your-feature-name
```

### 2. Write Your Code

Write your code following our [coding standards](#-coding-standards).

### 3. Format Your Code

**Before committing**, always format your code to ensure it meets our style guidelines:

```bash
# Format Java code (required)
mvn spotless:apply

# Format frontend code if you modified it
cd himarket-web/himarket-admin
npm run format
```

### 4. Commit Your Changes

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification. Your commit messages should be clear and descriptive:

```bash
git add .
git commit -m "feat: add user authentication feature"
```

**Commit message format:**
```
type: brief description (50 chars or less)

[Optional detailed explanation if needed]
```

**Common types:**
- `feat` - A new feature
- `fix` - A bug fix
- `docs` - Documentation changes
- `style` - Code style changes (formatting, missing semicolons, etc.)
- `refactor` - Code refactoring without changing functionality
- `perf` - Performance improvements
- `test` - Adding or updating tests
- `chore` - Maintenance tasks, dependency updates, etc.

### 5. Push to Your Fork

```bash
git push origin feat/your-feature-name
```

### 6. Create a Pull Request

1. Go to your fork on GitHub
2. Click the **"New Pull Request"** button
3. Ensure the base repository is `higress-group/himarket` and the base branch is `main`
4. Select your feature branch as the compare branch
5. Fill in the **PR template** (auto-loaded) with details about your changes
6. Click **"Create Pull Request"**

---

## 📝 Pull Request Guidelines

### PR Title

Your PR title must follow the format: `type: brief description`

**Good examples:**
```
✅ feat: add product feature configuration
✅ fix: resolve pagination issue in product list
✅ docs: update deployment guide in README
✅ refactor: simplify client initialization logic
```

**Bad examples:**
```
❌ Add new feature (missing type)
❌ feat: Add Feature (description should be lowercase)
❌ update code (not descriptive)
```

### PR Description

Your PR **must include** the following sections:

1. **Description** (Required)
   - Clearly explain what changes you made and why
   - Use bullet points for clarity
   - Minimum 10 characters

2. **Related Issues** (Optional but recommended)
   - Link to related issues using keywords: `Fix #123`, `Close #456`, `Resolve #789`
   - This helps us track which issues are being addressed

3. **Checklist** (Required)
   - Confirm you've run `mvn spotless:apply` to format your code
   - Indicate whether you've added tests or updated documentation
   - Check off applicable items

**Example PR description:**
```markdown
## Description

- Add feature configuration field to Product entity
- Create ModelFeatureForm component for the admin UI
- Implement backend service to persist feature settings
- Add Flyway migration script for database schema changes

## Related Issues

Fix #123
Close #456

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
- [x] Documentation updated (if applicable)
```

### Automated Checks

Every PR will automatically trigger the following checks:

1. **PR Check** - Validates your PR title and description format (Required ✅)
2. **Code Format Check** - Runs `mvn spotless:check` to verify code formatting (Required ✅)
3. **Checkstyle Check** - Checks code style and best practices (Warning ⚠️)

**Required checks** must pass before your PR can be merged. **Warning checks** won't block your PR but provide suggestions for improvement.

If a check fails, the bot will comment with instructions on how to fix it.

**For more detailed PR guidelines, please see:**
- [PR_GUIDE.md](.github/PR_GUIDE.md) - English version
- [PR_GUIDE_CN.md](.github/PR_GUIDE_zh) - 中文版本

---

## 💻 Coding Standards

### Java Code

**Code Formatting (Required):**
- Run `mvn spotless:apply` before committing to auto-format your code
- This ensures consistent code style across the project
- **CI will fail** if code is not formatted

**Code Style (Recommended):**
- We use Checkstyle to check for best practices and style issues
- Run `mvn checkstyle:check` locally to see suggestions
- **CI will warn** but not block if there are violations

**Best Practices:**
- Maximum 120 characters per line
- Use clear, descriptive names for variables, methods, and classes
- Add Javadoc comments for public APIs
- Avoid magic numbers and empty catch blocks
- Keep methods focused and reasonably sized

### TypeScript/React Code

- **Formatting**: Use Prettier to format your code: `npm run format`
- **Style Guide**: Follow the [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)
- **Type Safety**: Always use TypeScript types/interfaces; avoid `any` when possible
- **Components**: Prefer functional components with React Hooks
- **Naming**: Use descriptive names; follow PascalCase for components, camelCase for functions

### Database Migrations

- **Tool**: Use Flyway for all database schema changes
- **Location**: Place migration files in `himarket-bootstrap/src/main/resources/db/migration/`
- **Naming**: Follow the pattern `V{version}__{description}.sql`
  - Example: `V3__Add_product_feature.sql`
- **Testing**: Always test your migrations on a clean database before submitting

