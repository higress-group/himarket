=== PR Body ===
<!-- 
🎉 Thank you for contributing to HiMarket!

Please follow these guidelines:

1️⃣ PR Title Format
   - Format: type: description
   - Example: feat: add product feature
   - Description must start with lowercase letter or Chinese characters
   - Chinese example: feat: 添加产品特性配置

2️⃣ Description (Required)
   - Describe your changes in detail
   - At least 10 characters

3️⃣ Type of Change (Required)
   - Check at least one type
   
4️⃣ Testing (Recommended)
   - Describe how you tested these changes

5️⃣ Related Issues (Recommended)
   - Link issues using Fix #123 or Close #456

📚 For Detailed Guidelines:
- English: .github/PR_GUIDE.md
- 中文: .github/PR_GUIDE_zh.md
-->

## 📝 Description

<!-- 
Required: Describe your changes in detail (at least 10 characters)
Tip: You can use bullet points for clarity
-->

Support HiMarket autonomous API management capabilities:
- Support creating APIs directly from HiMarket
- Support managing APIs within HiMarket

<!-- 
Examples:
- Refactored user authentication module to improve performance
- Added caching mechanism for frequently accessed data
- Fixed a bug where session timeout was not properly handled
-->



## 🔗 Related Issues

<!-- 
Optional but recommended: 
Link related issues using keywords like "Fix", "Close", or "Resolve"
-->

<!-- 
Examples:
- Fix #123
- Close #456, Resolve #789
- Related to #100
-->



## ✅ Type of Change

<!-- 
Required: Check at least one type
-->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [x] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Code refactoring (no functional changes)
- [ ] Performance improvement
- [x] Build/CI configuration change
- [ ] Other (please describe):



## 🧪 Testing

<!-- 
Optional but recommended: 
Describe how you tested these changes
-->

Verified local build and deployment.

<!-- 
Examples:
- Ran all unit tests in local environment
- Manually tested various scenarios of the new feature
- Added new integration test cases
-->

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [x] Manual testing completed
- [x] All tests pass locally



## 📋 Checklist

<!-- 
Please check the items that apply to your PR
-->

- [ ] Code has been formatted (`mvn spotless:apply` for backend, `npm run lint:fix` for frontend)
- [ ] Code is self-reviewed
- [ ] Comments added for complex code
- [ ] Documentation updated (if applicable)
- [ ] No breaking changes (or migration guide provided)
- [ ] All CI checks pass



## 📊 Test Coverage

<!-- 
Optional: 
If code was modified, does test coverage remain or improve?
-->

<!-- 
Examples:
- Added 15 new unit tests
- Overall coverage increased from 65% to 68%
- All critical paths are covered
-->



## 📚 Additional Notes

<!-- 
Optional: 
Any additional information reviewers should know
-->

<!-- 
Examples:
- This change requires database migration
- Performance testing shows 20% improvement
- Breaking change: API endpoint path changed
-->



===============

## 📋 PR Content Check Report

### ❌ Required items need attention

❌ Missing description or description too short (at least 10 characters required)
   👉 Please provide a detailed description of your changes in the "Description" section.

❌ No change type selected
   👉 Please select at least one change type in the "Type of Change" section (Bug fix, New feature, etc.).

### 💡 Suggestions (Optional)

💡 Consider describing how you tested these changes

### 📊 PR Size Check

⚠️ **Large PR** (18469 lines): Consider splitting into smaller PRs for easier review.

---

### 📝 PR Content Requirements

**Required:**
- **Description**: Clear explanation of your changes (at least 10 characters)
- **Type of Change**: Check at least one change type

**Optional but recommended:**
- **Related Issues**: Link issues using `Fix #123`, `Close #456`, etc.
- **Testing**: Describe how you tested these changes
- **Checklist**: Check other relevant items (code formatting, self-review, tests, etc.)

**Example format:**
```markdown
## 📝 Description

- Refactored client initialization method
- Optimized parameter handling logic

## 🔗 Related Issues

Fix #123

## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)

## 🧪 Testing

- [x] Manual testing completed
- [x] All tests pass locally
```

Error: Missing description or description too short (at least 10 characters required)
Error: No change type selected
Warning: Consider describing how you tested these changes
Error: PR content check failed: 2 required item(s) incomplete