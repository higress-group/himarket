# Pull Request Guidelines

This document describes the PR submission guidelines for the Himarket project.

## PR Title Format

### Required Format

```
type: brief description
```

or with scope:

```
type(scope): brief description
```

### Allowed Types

| Type | Description | Example |
|------|-------------|---------|
| `feat` | New feature | `feat: add user authentication` |
| `fix` | Bug fix | `fix: resolve memory leak` |
| `docs` | Documentation | `docs: update API documentation` |
| `style` | Code formatting | `style: format with prettier` |
| `refactor` | Code refactoring | `refactor: simplify service logic` |
| `perf` | Performance | `perf: optimize queries` |
| `test` | Testing | `test: add unit tests` |
| `build` | Build system | `build: update dependencies` |
| `ci` | CI/CD | `ci: add workflow` |
| `chore` | Other changes | `chore: update gitignore` |
| `revert` | Revert commit | `revert: revert commit abc123` |

### Title Rules

1. ✅ Must include type prefix
2. ✅ Colon and space after type: `feat: ` not `feat:`
3. ✅ Description must start with lowercase letter
4. ✅ Keep it brief and clear (recommended < 50 characters)

### ✅ Correct Examples

```
✅ feat: add product feature configuration
✅ fix: resolve pagination issue in product list
✅ docs: update deployment guide
✅ feat(product): add feature configuration support
✅ refactor(api): simplify product service
✅ perf: optimize database query performance
```

### ❌ Wrong Examples

```
❌ Add product feature                  (missing type)
❌ feat: Add Feature                    (uppercase first letter)
❌ featadd feature                      (missing colon and space)
❌ feature: add feature                 (invalid type, should be "feat")
❌ feat:add feature                     (missing space after colon)
```

---

## PR Content Format

### Required Sections

#### 1. Description (Required)

Must include a `## Description` section with at least 10 characters of meaningful content.

**Format:**
```markdown
## Description

[Your changes - minimum 10 characters]
```

**You can use:**
- Bullet points (recommended)
- Paragraphs
- Mixed format

**Examples:**

**Style 1: Bullet Points (Recommended)**
```markdown
## Description

- Add feature field to product DTO
- Create ModelFeatureForm component
- Update product service logic
- Add database migration script
```

**Style 2: Paragraphs**
```markdown
## Description

This PR adds product feature configuration functionality for MODEL_API 
products. Users can now configure model parameters directly from the 
admin panel.
```

**Style 3: Detailed**
```markdown
## Description

### Changes
- Refactored ClientFactory class
- Added ErrorHandler utility
- Updated configuration loading

### Benefits
- Improved code readability
- Better error messages
- 20% faster initialization
```

#### 2. Related Issues (Optional but Recommended)

Link related issues to help track what's being fixed.

**Format:**
```markdown
## Related Issues

Fix #123
Close #456
```

**Supported Keywords:**
- `Fix #123` / `Fixes #123` / `Fixed #123`
- `Close #123` / `Closes #123` / `Closed #123`
- `Resolve #123` / `Resolves #123` / `Resolved #123`

When the PR is merged, linked issues will be automatically closed.

#### 3. Checklist (Required: Code Formatting)

The checklist helps ensure code quality and completeness.

**Format:**
```markdown
## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [ ] Tests added/updated (if applicable)
- [ ] Documentation updated (if applicable)
- [ ] No breaking changes (or migration guide provided)
```

**Required Item:**
- ✅ **Code has been formatted with `mvn spotless:apply`** - This item MUST be checked

**Optional Items:**
- Code is self-reviewed
- Tests added/updated (if applicable)
- Documentation updated (if applicable)
- No breaking changes (or migration guide provided)

**Important:** Before submitting your PR, you must:
1. Run `mvn spotless:apply` in your project root
2. Check the "Code has been formatted" box in the Checklist
3. Commit any formatting changes

---

## Automated Checks

Every PR will automatically trigger two checks:

### 1. PR Title Check

**Validates:**
- ✅ Type prefix is present and valid
- ✅ Format includes colon and space
- ✅ Description starts with lowercase letter

**Result:**
- ✅ Pass: Title format is correct
- ❌ Fail: Title format error (with detailed explanation)

### 2. PR Content Check

**Validates:**
- ✅ `## Description` section exists
- ✅ Description content is at least 10 characters
- ✅ `## Checklist` section exists
- ✅ "Code has been formatted with `mvn spotless:apply`" is checked

**Optional Checks (warnings only):**
- 💡 Suggests linking issues if not present
- 💡 Suggests adding more details if description is very short (< 50 chars)

**Result:**
- ✅ Pass: All required sections present and code formatting confirmed
- ❌ Fail: Missing description, too short, or code formatting not confirmed
- 💡 Suggestion: Recommendations for improvement

---

## Complete Examples

### Example 1: Feature PR ✅

**Title:**
```
feat: add product feature configuration
```

**Content:**
```markdown
## Description

- Add feature field to product DTO and database schema
- Create ModelFeatureForm component for configuration UI
- Update product service to persist feature configurations
- Add database migration script for the new column

## Related Issues

Fix #123
Close #456

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
- [x] Documentation updated (if applicable)
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
```

---

### Example 2: Bug Fix PR ✅

**Title:**
```
fix: resolve pagination issue in product list
```

**Content:**
```markdown
## Description

Fixed SQL injection vulnerability in product list pagination by 
replacing string concatenation with parameterized queries.

Testing: Verified with 10,000+ records.

## Related Issues

Fix #789

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
```

---

### Example 3: Simple Refactoring ✅

**Title:**
```
refactor: simplify client initialization
```

**Content:**
```markdown
## Description

- Extract initialization logic to separate method
- Remove duplicate code
- Add inline documentation

## Related Issues

None

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
💡 Suggestion: Consider linking related issues
```

---

## Common Mistakes

### Mistake 1: Wrong Title Format

**Wrong:**
```
Add new feature
```

**Correct:**
```
feat: add new feature
```

**Error Message:**
```
❌ PR title format is incorrect!
Missing type prefix. Expected format: type: description
```

---

### Mistake 2: Uppercase Description

**Wrong:**
```
feat: Add New Feature
```

**Correct:**
```
feat: add new feature
```

**Error Message:**
```
❌ PR title format is incorrect!
Subject must start with lowercase letter
```

---

### Mistake 3: Missing Description Section

**Wrong:**
```markdown
This PR adds new feature.

## Related Issues
Fix #123
```

**Correct:**
```markdown
## Description

This PR adds new feature.

## Related Issues
Fix #123
```

**Error Message:**
```
❌ Missing description or too short (at least 10 characters required)
```

---

### Mistake 4: Description Too Short

**Wrong:**
```markdown
## Description

Fix bug
```
(Only 7 characters)

**Correct:**
```markdown
## Description

Fix pagination bug in product list
```

**Error Message:**
```
❌ Missing description or too short (at least 10 characters required)
```

---

### Mistake 5: Code Formatting Not Confirmed

**Wrong:**
```markdown
## Description

Add new feature

## Checklist

- [ ] Code has been formatted with `mvn spotless:apply`  <!-- Not checked -->
- [x] Code is self-reviewed
```

**Correct:**
```markdown
## Description

Add new feature

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`  <!-- Must be checked -->
- [x] Code is self-reviewed
```

**Error Message:**
```
❌ Please confirm code has been formatted with `mvn spotless:apply`
```

**Note:** You must:
1. Run `mvn spotless:apply` in your terminal
2. Commit any formatting changes
3. Check the box in the Checklist

---

## FAQ

### Q: Do I need to fill in all sections?

**A:** Only the `## Description` section is required. `## Related Issues` is optional but recommended.

---

### Q: Can I use Chinese in the description?

**A:** Yes, but we recommend using English for better collaboration. The title must follow the English format.

---

### Q: What if my PR doesn't fix any issue?

**A:** That's fine! You can write "None" in the Related Issues section or leave it empty. It won't cause the check to fail.

---

### Q: Can I write the description in paragraph format?

**A:** Absolutely! Any format is fine as long as it's clear and at least 10 characters. Bullet points are just recommended for readability.

---

### Q: What happens if the check fails?

**A:** 
1. You'll see a ❌ mark on your PR
2. The bot will comment with specific errors
3. Edit your PR title or description to fix the issues
4. The check will automatically re-run

---

### Q: Can I bypass the check?

**A:** No, but project maintainers can override if there's a valid reason. Generally, following the guidelines is quick and easy.

---

### Q: Why must the title start with lowercase?

**A:** This is a widely adopted convention (Conventional Commits). It keeps commit history clean and consistent.

---

### Q: What if I make multiple unrelated changes?

**A:** Consider splitting into separate PRs. If they must be together, describe all changes clearly in the Description section.