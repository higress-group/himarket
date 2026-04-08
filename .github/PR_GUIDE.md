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

#### 1. Description (Required) 📝

Must include a `## 📝 Description` section with at least 10 characters of meaningful content.

**Format:**
```markdown
## 📝 Description

[Your changes - minimum 10 characters]
```

**You can use:**
- Bullet points (recommended)
- Paragraphs
- Mixed format

**Examples:**

**Style 1: Bullet Points (Recommended)**
```markdown
## 📝 Description

- Add feature field to product DTO
- Create ModelFeatureForm component
- Update product service logic
- Add database migration script
```

**Style 2: Paragraphs**
```markdown
## 📝 Description

This PR adds product feature configuration functionality for MODEL_API 
products. Users can now configure model parameters directly from the 
admin panel.
```

**Style 3: Detailed**
```markdown
## 📝 Description

### Changes
- Refactored ClientFactory class
- Added ErrorHandler utility
- Updated configuration loading

### Benefits
- Improved code readability
- Better error messages
- 20% faster initialization
```

#### 2. Type of Change (Required) ✅

Must select at least one type of change by checking a checkbox.

**Format:**
```markdown
## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement
```

**Available Options:**
- **Bug fix** - Fixes an issue without breaking existing functionality
- **New feature** - Adds new functionality without breaking changes
- **Breaking change** - Changes that would cause existing functionality to not work as expected
- **Documentation update** - Changes to documentation only
- **Code refactoring** - Code changes without functional impact
- **Performance improvement** - Optimizations that improve performance
- **Build/CI configuration change** - Changes to build or CI/CD configuration
- **Other** - Any other type (please describe)

**Important:** You must check at least one box. This helps reviewers quickly understand the nature of your changes.

#### 3. Related Issues (Optional but Recommended) 🔗

Link related issues to help track what's being fixed.

**Format:**
```markdown
## 🔗 Related Issues

Fix #123
Close #456
```

**Supported Keywords:**
- `Fix #123` / `Fixes #123` / `Fixed #123`
- `Close #123` / `Closes #123` / `Closed #123`
- `Resolve #123` / `Resolves #123` / `Resolved #123`

When the PR is merged, linked issues will be automatically closed.

#### 4. Testing (Optional but Recommended) 🧪

Describe how you tested your changes to ensure quality and reliability.

**Format:**
```markdown
## 🧪 Testing

- [x] Unit tests added/updated
- [x] Integration tests added/updated
- [x] Manual testing completed
- [x] All tests pass locally
```

**Testing Guidelines:**
- Describe what testing was performed
- Include test results or verification steps
- Note any edge cases tested
- Confirm all tests pass locally

**Example:**
```markdown
## 🧪 Testing

- Added unit tests for new feature configuration logic
- Manually tested with various product types
- Verified backward compatibility with existing products
- All 127 tests pass locally
```

#### 5. Checklist (Required Items) 📋

The checklist helps ensure code quality and completeness.

**Format:**
```markdown
## 📋 Checklist

- [x] Code quality checks passed (run `./scripts/code-check.sh`)
- [x] Code is self-reviewed
- [x] Comments added for complex code
- [x] Documentation updated (if applicable)
- [x] No breaking changes (or migration guide provided)
- [x] All CI checks pass
```

**Recommended Items:**
- ✅ **Code quality checks passed** - Run `./scripts/code-check.sh` from the repository root before submitting
- ✅ **Code is self-reviewed** - Review your own changes first
- Comments added for complex code
- Documentation updated (if applicable)
- No breaking changes (or migration guide provided)
- All CI checks pass

**Important:** Before submitting your PR:
1. **Recommended:** Run `./scripts/code-check.sh` from the repository root
2. Review your own code changes
3. Commit any formatting or auto-fix changes produced by the checks

#### 6. Test Coverage (Optional) 📊

If you modified code, indicate whether test coverage was maintained or improved.

**Format:**
```markdown
## 📊 Test Coverage

- Added 15 new unit tests
- Overall coverage increased from 65% to 68%
- All critical paths are covered
```

#### 7. Additional Notes (Optional) 📚

Any additional context or information reviewers should know.

**Format:**
```markdown
## 📚 Additional Notes

- This change requires database migration
- Performance testing shows 20% improvement
- Breaking change: API endpoint path changed
```

---

## Automated Checks

Every PR will automatically trigger three types of checks:

### 1. PR Title Check

**Validates:**
- ✅ Type prefix is present and valid
- ✅ Format includes colon and space
- ✅ Description starts with lowercase letter

**Result:**
- ✅ Pass: Title format is correct
- ❌ Fail: Title format error (with detailed explanation)

### 2. PR Content Check

**Required Items (must pass):**
- ✅ `## 📝 Description` section exists
- ✅ Description content is at least 10 characters
- ✅ `## ✅ Type of Change` section exists
- ✅ At least one type is checked (e.g., `- [x] Bug fix`)

**Optional Checks (recommendations only):**
- 💡 Suggests linking issues if not present (`Fix #123`)
- 💡 Suggests adding testing information
- 💡 Warns if PR is very large (> 500 or > 1000 lines)

**Result:**
- ✅ Pass: All required sections present with valid content
- ❌ Fail: Missing description, too short, or no type selected
- 💡 Suggestion: Recommendations for improvement

### 3. PR Size Check

**Evaluates:**
- 📊 Total lines changed (additions + deletions)
- 📁 Number of files changed

**Size Categories:**
- 🟢 **XS** (< 100 lines): Excellent - easy to review
- 🟢 **S** (100-300 lines): Good - reasonable size
- 🟡 **M** (300-600 lines): Medium - ensure focused scope
- 🟠 **L** (600-1000 lines): Large - consider splitting
- 🔴 **XL** (> 1000 lines): Very large - strongly recommend splitting

**Result:**
- Always passes (informational only)
- Provides recommendations for large PRs
- Does not block PR submission

---

## Complete Examples

### Example 1: Feature PR ✅

**Title:**
```
feat: add product feature configuration
```

**Content:**
```markdown
## 📝 Description

- Add feature field to product DTO and database schema
- Create ModelFeatureForm component for configuration UI
- Update product service to persist feature configurations
- Add database migration script for the new column

## 🔗 Related Issues

Fix #123
Close #456

## ✅ Type of Change

- [x] New feature (non-breaking change)
- [ ] Bug fix (non-breaking change)

## 🧪 Testing

- [x] Unit tests added/updated
- [x] Manual testing completed
- Tested with 100+ products, all configurations saved correctly

## 📋 Checklist

- [x] Code quality checks passed (run `./scripts/code-check.sh`)
- [x] Code is self-reviewed
- [x] Comments added for complex code
- [x] Documentation updated (if applicable)
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
✅ pr-size-check: Passed (250 lines - Size: S)
```

---

### Example 2: Bug Fix PR ✅

**Title:**
```
fix: resolve pagination issue in product list
```

**Content:**
```markdown
## 📝 Description

Fixed SQL injection vulnerability in product list pagination by 
replacing string concatenation with parameterized queries.

## 🔗 Related Issues

Fix #789

## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)

## 🧪 Testing

- [x] Unit tests added/updated
- [x] Manual testing completed
- Verified with 10,000+ records - no performance degradation
- Security scan shows vulnerability is resolved

## 📋 Checklist

- [x] Code quality checks passed (run `./scripts/code-check.sh`)
- [x] Code is self-reviewed
- [x] All CI checks pass
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
✅ pr-size-check: Passed (85 lines - Size: XS)
```

---

### Example 3: Simple Refactoring ✅

**Title:**
```
refactor: simplify client initialization
```

**Content:**
```markdown
## 📝 Description

- Extract initialization logic to separate method
- Remove duplicate code
- Add inline documentation

## 🔗 Related Issues

None

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [x] Code refactoring (no functional changes)

## 🧪 Testing

- [x] All tests pass locally
- No new tests needed - refactoring only
- Verified existing functionality unchanged

## 📋 Checklist

- [x] Code quality checks passed (run `./scripts/code-check.sh`)
- [x] Code is self-reviewed
- [x] No breaking changes
```

**Check Result:**
```
✅ pr-title-check: Passed
✅ pr-content-check: Passed
✅ pr-size-check: Passed (120 lines - Size: S)
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
## 📝 Description

Fix bug
```
(Only 7 characters)

**Correct:**
```markdown
## 📝 Description

Fix pagination bug in product list
```

**Error Message:**
```
❌ Missing description or too short (at least 10 characters required)
```

---

### Mistake 5: No Type of Change Selected

**Wrong:**
```markdown
## 📝 Description

Add new feature

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
```

**Correct:**
```markdown
## 📝 Description

Add new feature

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [x] New feature (non-breaking change)
- [ ] Breaking change
```

**Error Message:**
```
❌ 未选择变更类型
Please select at least one Type of Change checkbox
```

**Note:** You must check at least one box to indicate what kind of change your PR introduces.

---

### Mistake 6: Code Quality Checks Not Confirmed

**Wrong:**
```markdown
## 📝 Description

Add new feature

## 📋 Checklist

- [ ] Code quality checks passed (run `./scripts/code-check.sh`)  <!-- Not checked -->
- [x] Code is self-reviewed
```

**Correct:**
```markdown
## 📝 Description

Add new feature

## 📋 Checklist

- [x] Code quality checks passed (run `./scripts/code-check.sh`)  <!-- Must check -->
- [x] Code is self-reviewed
```

**Note:** Before submitting:
1. Run `./scripts/code-check.sh` from the repository root
2. Commit any formatting or auto-fix changes
3. Verify the checklist items apply to your changes

---

## FAQ

### Q: Do I need to fill in all sections?

**A:** Required sections:
- ✅ `## 📝 Description` (at least 10 characters)
- ✅ `## ✅ Type of Change` (check at least one box)

Optional but recommended:
- 💡 `## 🔗 Related Issues` (link to related issues)
- 💡 `## 🧪 Testing` (describe testing performed)
- 💡 `## 📋 Checklist` (self-review items)
- 💡 `## 📊 Test Coverage` (coverage information)
- 💡 `## 📚 Additional Notes` (extra context)

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