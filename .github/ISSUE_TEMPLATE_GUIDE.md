# Issue Template Guide

## 📋 Overview

HiMarket uses structured Issue templates to help contributors provide clear, actionable information. When creating a new Issue, you'll be presented with several templates to choose from.

## 🎯 Template Types

### 1. 🐛 Bug Report

**When to use:**
- Something is broken or not working as expected
- You've encountered an error or unexpected behavior
- A feature is not functioning correctly

**What you'll provide:**
- Clear description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, browser, versions)
- Error messages and logs

**Labels:** `bug`, `needs-triage`

---

### 2. ✨ Feature Request

**When to use:**
- You have an idea for a new feature
- You want to suggest an enhancement
- You've identified a missing capability

**What you'll provide:**
- Problem statement (what need does this address?)
- Proposed solution
- Alternative approaches considered
- Use cases and examples
- Priority and importance

**Labels:** `enhancement`, `needs-review`

---

### 3. ❓ Question

**When to use:**
- You need help understanding how something works
- You're looking for guidance on best practices
- You have a "how-to" question

**What you'll provide:**
- Your question in detail
- What you're trying to accomplish
- What you've already tried
- Relevant context and code

**Labels:** `question`

**💡 Tip:** For complex discussions or open-ended topics, consider using [GitHub Discussions](https://github.com/higress-group/himarket/discussions) instead!

---

## 🚀 Creating an Issue

### Step 1: Choose a Template

1. Go to [Issues](https://github.com/higress-group/himarket/issues)
2. Click "New Issue"
3. Select the appropriate template:
   - 🐛 Bug Report
   - ✨ Feature Request
   - ❓ Question

### Step 2: Fill Out the Form

- **Required fields** are marked with an asterisk (*)
- Fill in as much detail as possible
- Use the provided placeholders as guidance
- Add screenshots, code snippets, or logs when relevant

### Step 3: Submit

- Review your submission for completeness
- Check the required checkboxes
- Click "Submit new issue"

---

## ✅ Best Practices

### For Bug Reports

```markdown
✅ DO:
- Provide exact steps to reproduce
- Include error messages verbatim
- Specify your environment details
- Test with the latest version first

❌ DON'T:
- Say "it doesn't work" without details
- Skip reproduction steps
- Omit error messages
- Report multiple unrelated bugs in one issue
```

### For Feature Requests

```markdown
✅ DO:
- Explain the problem you're trying to solve
- Describe your use case clearly
- Consider implementation complexity
- Provide examples or mockups

❌ DON'T:
- Request features without explaining why
- Skip the "alternatives considered" section
- Demand unrealistic timelines
- Be vague about requirements
```

### For Questions

```markdown
✅ DO:
- Search existing issues first
- Provide context about what you're trying to do
- Show what you've already tried
- Include relevant code/configuration

❌ DON'T:
- Ask without reading the documentation
- Expect immediate answers
- Post duplicate questions
- Ask multiple unrelated questions in one issue
```

---

## 🏷️ Issue Labels

Issues will be automatically labeled based on the template used:

| Label | Description | Auto-applied |
|-------|-------------|--------------|
| `bug` | Something isn't working | ✅ Bug Report |
| `enhancement` | New feature or request | ✅ Feature Request |
| `question` | Further information is requested | ✅ Question |
| `needs-triage` | Needs initial review | ✅ Bug Report |
| `needs-review` | Needs team discussion | ✅ Feature Request |

Additional labels may be added by maintainers:

| Label | Description |
|-------|-------------|
| `good first issue` | Good for newcomers |
| `help wanted` | Extra attention is needed |
| `wontfix` | This will not be worked on |
| `duplicate` | This issue already exists |
| `invalid` | This doesn't seem right |
| `priority: high` | High priority |
| `priority: low` | Low priority |
| `frontend` | Frontend related |
| `backend` | Backend related |
| `documentation` | Documentation related |

---

## 🔗 Additional Resources

- **Blank Issues Disabled**: We require using templates to ensure quality submissions
- **Contact Links** (in case you need something else):
  - 💬 [Discussion Forum](https://github.com/higress-group/himarket/discussions) - For general discussions
  - 📖 [Documentation](https://github.com/higress-group/himarket) - Check guides and tutorials
  - 🐛 [Security Issue](https://github.com/higress-group/himarket/security/advisories/new) - Report vulnerabilities privately

---

## 📝 Example Issues

### Good Bug Report Example

```
Title: [Bug]: Product search returns empty results for Chinese characters

**Bug Description**
When searching for products using Chinese characters (e.g., "产品"), the search 
returns no results, even though products with Chinese names exist in the database.

**Steps to Reproduce**
1. Navigate to the product search page
2. Enter "产品" in the search box
3. Click the search button
4. Observe that no results are returned

**Expected Behavior**
Products with Chinese characters in their names should appear in search results.

**Actual Behavior**
No results are returned. English searches work correctly.

**Environment**
- OS: macOS 13.0
- Browser: Chrome 120
- HiMarket Version: v1.0.0
- Database: MySQL 8.0
```

### Good Feature Request Example

```
Title: [Feature]: Add bulk import for products via CSV

**Problem Statement**
Currently, products must be added one at a time through the admin interface. 
For merchants with large catalogs (500+ products), this is extremely time-consuming.

**Proposed Solution**
Add a CSV import feature that allows admins to:
1. Download a CSV template
2. Fill in product details
3. Upload the CSV to create multiple products at once

**Use Cases**
- New merchants onboarding with existing product catalogs
- Regular bulk updates to pricing or inventory
- Seasonal product catalog changes

**Acceptance Criteria**
- [ ] CSV template generation
- [ ] CSV validation before import
- [ ] Error reporting for invalid rows
- [ ] Progress indicator during import
- [ ] Rollback on critical errors
```

---

## 💡 Tips for Contributors

1. **Search First**: Before creating an issue, search existing issues to avoid duplicates
2. **Be Specific**: Vague issues are hard to address
3. **One Issue, One Topic**: Don't combine multiple bugs or features
4. **Follow Up**: Respond to questions from maintainers
5. **Be Patient**: Open source maintainers are often volunteers

---

## 🤝 Getting Help

If you're unsure which template to use or need help:

1. Check the [Contributing Guide](../CONTRIBUTING.md)
2. Ask in [Discussions](https://github.com/higress-group/himarket/discussions)
3. Review similar existing issues

---

Thank you for contributing to HiMarket! 🎉

