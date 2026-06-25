# Backend Dependency Management

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Maven Dependency Management

HiMarket is a multi-module Maven project. Dependency and plugin versions must be managed
centrally in the parent `pom.xml` so all modules resolve consistent versions and upgrades are
reviewed in one place.

**Rules:**

- Declare third-party dependency versions in the parent POM through `<properties>` and
  `<dependencyManagement>`.
- Declare Maven plugin versions in the parent POM through `<properties>` and `<pluginManagement>`.
- Submodule POM files should declare managed dependencies without `<version>`. The required
  `<parent><version>...</version></parent>` entry is the normal Maven exception.
- Add or upgrade a third-party dependency in the parent POM first, then reference it from the
  required module POM.
- Do not duplicate the same dependency version across module POM files. Existing direct versions in
  module POM files should be treated as cleanup debt and not copied.
- Manage internal module dependencies in the parent POM with `${project.version}`. Module POM files
  should depend on sibling modules without hard-coded versions.
- Keep dependency scope explicit. Use `test`, `runtime`, `provided`, or optional dependencies only
  when the module really requires that scope.
- Test-only dependencies must use `<scope>test</scope>`.
- Do not use version ranges, `LATEST`, or `RELEASE`.
- Avoid adding a new dependency when the JDK, Spring, existing project utilities, or a current
  dependency already provides the capability.
- Do not add Hutool, Guava, or Apache Commons as broad replacements for basic JDK, Spring, or
  project utility functionality.
- When replacing or upgrading a dependency, check all modules that use it and run the relevant
  build or tests.

```xml
<!-- parent pom.xml -->
<properties>
    <example-lib.version>1.2.3</example-lib.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>example-lib</artifactId>
            <version>${example-lib.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- module pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-lib</artifactId>
</dependency>
```

---


## Utility Selection

Prefer platform and project utilities before adding dependencies.

| Need | Preferred choice |
| ---- | ---------------- |
| String checks and equality | Project `Strings` helper |
| Collection emptiness | JDK collection APIs or Spring `CollectionUtils` |
| Base64 | JDK `Base64` with `StandardCharsets.UTF_8` |
| JSON | Project `JsonUtil` |
| Business IDs | Project `IdGenerator` |
| Simple hashing for non-secret cache keys | Spring `DigestUtils` or existing project helper |
| Application events | Constructor-injected `ApplicationEventPublisher` |

Keep project utility classes narrow. A helper such as `Strings` should only contain common string
semantics; it should not grow unrelated formatting, joining, collection, or business-specific
methods.

---
