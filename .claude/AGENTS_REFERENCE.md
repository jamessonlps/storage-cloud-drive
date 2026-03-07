# Agents Reference Guide

Quick reference for all available project agents and their capabilities.

## Agent Overview

| Agent | Type | Model | Purpose | Triggers |
|-------|------|-------|---------|----------|
| kotlin-unit-test-builder | Testing | Haiku | Write comprehensive unit tests | Code written, manual request |
| code-quality-reviewer | Analysis | Haiku | Review code quality & security | PR opened, manual request |
| android-build-deployer | CI/CD | Haiku | Build, test, sign, release | Manual request |
| documentation-generator | Documentation | Haiku | Generate docs & API documentation | Manual request |
| architecture-advisor | Architecture | Haiku | Guide architectural decisions | Manual request |

## Detailed Agent Descriptions

### 1. Kotlin Unit Test Builder
- **File**: `.claude/agents/kotlin-unit-test-builder.md`
- **Color**: Purple
- **Specialization**: JUnit 5, Mockito, Coroutine Testing

**What it does**:
- Analyzes Kotlin code
- Identifies testable units
- Generates comprehensive test cases
- Covers normal paths, edge cases, error scenarios
- Uses Mockito for dependencies
- Handles coroutine testing with `runTest`

**When to request**:
```
"I wrote a new S3 upload function, can you create unit tests?"
```

**Test coverage includes**:
- Happy path (normal operation)
- Edge cases (empty, null, max values)
- Error scenarios (network failure, invalid input)
- State transitions
- Concurrency and race conditions

**Output**:
- Complete, runnable test classes
- All necessary imports
- Test fixtures and helpers
- Well-organized test methods
- Clear test naming convention

---

### 2. Code Quality Reviewer
- **File**: `.claude/agents/code-quality-reviewer.md`
- **Color**: Blue
- **Specialization**: Security, Performance, Best Practices

**What it does**:
- Reviews code for security vulnerabilities
- Identifies performance bottlenecks
- Checks Kotlin/Android best practices
- Validates error handling
- Ensures architecture consistency
- Verifies test coverage

**When to request**:
```
"Can you review this network request code for security issues?"
```

**Security checks**:
- No hardcoded credentials/API keys
- Proper use of Android security APIs
- Input validation at boundaries
- Secure network communication (HTTPS)
- Permission handling
- No deprecated security APIs

**Output**:
- Overall assessment (Good/Needs Review/Critical)
- Categorized issues (Security/Performance/Quality)
- Severity levels for each issue
- Specific, actionable suggestions
- Approval status when ready

---

### 3. Android Build Deployer
- **File**: `.claude/agents/android-build-deployer.md`
- **Color**: Orange
- **Specialization**: Gradle, Building, Signing, CI/CD

**What it does**:
- Manages debug and release builds
- Executes unit and integration tests
- Handles APK/Bundle signing
- Manages Gradle configuration
- Automates release workflows
- Troubleshoots build issues

**Common requests**:
```
"Build the release APK and sign it"
"Run all tests and show coverage"
"Help me troubleshoot this build error"
```

**Build capabilities**:
- `./gradlew assembleDebug` - Debug APK
- `./gradlew assembleRelease` - Release APK
- `./gradlew bundleRelease` - Android App Bundle
- `./gradlew test` - Unit tests
- `./gradlew lint` - Lint checks
- `./gradlew detekt` - Static analysis

**Output**:
- Build logs and diagnostics
- Test reports and coverage
- Signing status
- APK verification
- Artifact locations
- Troubleshooting guidance

---

### 4. Documentation Generator
- **File**: `.claude/agents/documentation-generator.md`
- **Color**: Green
- **Specialization**: API Docs, Architecture Diagrams, Guides

**What it does**:
- Generates KDoc comments for APIs
- Creates architecture diagrams
- Writes developer guides
- Maintains README and setup docs
- Creates troubleshooting guides
- Manages documentation structure

**When to request**:
```
"Document the S3Repository API with examples"
"Create an architecture diagram for this feature"
"Update the README with new setup steps"
```

**Documentation types**:
- **KDoc**: For all public APIs with @param, @return, @throws
- **Architecture Diagrams**: Component, data flow, sequence diagrams
- **Code Comments**: Explaining "why", not "what"
- **Guides**: Setup, troubleshooting, developer guides
- **Examples**: Real-world usage scenarios

**Output**:
- Markdown documentation
- Code examples and snippets
- Diagrams in Mermaid/ASCII format
- Complete and linked documentation
- Updated files ready to commit

---

### 5. Architecture Advisor
- **File**: `.claude/agents/architecture-advisor.md`
- **Color**: Red
- **Specialization**: System Design, Design Patterns, Refactoring

**What it does**:
- Evaluates architectural decisions
- Reviews design patterns
- Guides refactoring efforts
- Assesses technical debt
- Proposes solutions for features
- Ensures consistency with principles

**When to request**:
```
"I need to add file caching. How should I architect this?"
"Is this design pattern the best fit for our system?"
"Should we refactor the UI layer?"
```

**Analysis covers**:
- Scalability (handling thousands of files)
- Maintainability (code structure, testability)
- Performance (optimizations, bottlenecks)
- Security (vulnerabilities, best practices)
- Extensibility (future features)

**Current architecture**:
```
Data Layer: S3Repository, SettingsManager, S3ClientProvider
Service Layer: TransferService, Progress updates
UI Layer: FileListScreen, SettingsScreen (Jetpack Compose)
```

**Output**:
- Architecture assessment
- Recommendations with rationale
- Design patterns to use/avoid
- Refactoring suggestions
- Technical debt prioritization
- Implementation guidance

---

## How to Use Agents

### Direct Invocation
Ask an agent directly by name:
```
"Can the kotlin-unit-test-builder write tests for my upload function?"
"I need the code-quality-reviewer to check this S3 code"
"Architecture-advisor: Is our layered architecture appropriate here?"
```

### Implicit Triggers
Agents may automatically offer help:
```
"I just wrote a new function that handles file downloads"
→ kotlin-unit-test-builder automatically suggests writing tests

"I'm about to push this code"
→ code-quality-reviewer offers to review
```

### In Pull Requests
Agents assist with PR workflow:
```
PR opened → code-quality-reviewer analyzes changes
         → kotlin-unit-test-builder suggests tests
         → architecture-advisor reviews design decisions
PR approved → android-build-deployer prepares release
```

## Agent Commands Summary

### Quick Commands
```bash
# Build
./gradlew assembleDebug                    # Debug APK
./gradlew assembleRelease                  # Release APK
./gradlew bundleRelease                    # App Bundle

# Test
./gradlew test                             # Unit tests
./gradlew test jacocoTestReport           # With coverage

# Verify
./gradlew lint                             # Lint checks
./gradlew detekt                           # Static analysis

# Clean
./gradlew clean                            # Clean artifacts
```

## Workflow Examples

### Adding a Feature
1. **Design**: "Architecture-advisor: How should I design the offline sync feature?"
2. **Implement**: Write the feature code
3. **Review**: "Code-quality-reviewer: Review this offline sync code"
4. **Test**: "Kotlin-unit-test-builder: Write tests for the offline sync"
5. **Document**: "Documentation-generator: Document the offline sync API"
6. **Build**: "Android-build-deployer: Build and test everything"
7. **Deploy**: Create PR, merge, release

### Fixing a Bug
1. **Reproduce**: Create failing test
2. **Fix**: Implement the fix
3. **Verify**: "Android-build-deployer: Run all tests"
4. **Review**: "Code-quality-reviewer: Any issues with this fix?"
5. **Document**: Update CHANGELOG.md
6. **Deploy**: Merge to main

### Preparing a Release
1. **Version**: Update version in build.gradle.kts
2. **Build**: "Android-build-deployer: Build release APK and sign it"
3. **Test**: Verify all tests pass
4. **Document**: Update CHANGELOG.md
5. **Release**: Create GitHub release with artifacts
6. **Distribute**: Upload to Play Store or distribute APK

## Configuration Files

### Agent Configuration
- **File**: `.claude/agents.yaml`
- **Contains**: Agent definitions, workflows, integrations
- **Update when**: Adding new agents, changing workflows

### Project Automation
- **File**: `.claude/project-automation.yaml`
- **Contains**: Development workflows, CI/CD automation, scripts
- **Update when**: Adding automation, changing build process

### GitHub Workflows
- **Location**: `.github/workflows/`
- **Files**:
  - `build-and-test.yml` - Build, test, lint on every commit
  - `release.yml` - Manual release workflow
- **Update when**: Changing CI/CD process

## Next Steps

1. **Try an agent**: Ask one agent to analyze your current code
2. **Read the guides**: Review DEVELOPMENT_GUIDE.md for workflows
3. **Set up automation**: Configure your IDE to use these agents
4. **Document patterns**: Let agents document your development process
5. **Optimize workflow**: Customize agent configuration for your needs

## Support

For agent-specific help:
- Each agent has detailed capabilities in its `.md` file
- DEVELOPMENT_GUIDE.md shows workflow examples
- agents.yaml contains agent configuration
- CLAUDE.md has project architecture overview

For issues or questions:
- Check CLAUDE.md for project guidelines
- Review existing agent documentation
- Ask agents directly - they're here to help!
