# Development Guide

This guide explains how to use the agent-based development workflow for this Android project.

## Available Agents

### 1. Kotlin Unit Test Builder
**Purpose**: Generate comprehensive unit tests for Kotlin code

**When to use**:
- After writing new functions or classes
- To improve test coverage
- To test complex logic with multiple scenarios

**Example**:
```
I just wrote a suspend function called fetchS3Files() that lists objects from S3
```
The agent will automatically suggest comprehensive unit tests covering:
- Normal operation with files
- Empty bucket scenario
- Network failure handling
- Invalid credentials handling

**Agent capabilities**:
- Generates JUnit 5 tests with Mockito
- Creates parameterized tests for multiple scenarios
- Handles coroutine testing with `runTest`
- Tests edge cases and error conditions

### 2. Code Quality Reviewer
**Purpose**: Review code for quality, security, and best practices

**When to use**:
- Before committing significant changes
- When you want a second opinion on implementation
- To catch security issues early
- To ensure consistency with project standards

**Example**:
```
Can you review this S3 upload implementation for security and performance?
```

**Agent analyzes**:
- Security vulnerabilities (hardcoded secrets, insecure APIs)
- Performance bottlenecks and memory leaks
- Kotlin/Android best practices
- Error handling and logging
- Architecture adherence

**Output**: Detailed review with specific, actionable suggestions

### 3. Android Build Deployer
**Purpose**: Manage builds, testing, signing, and releases

**When to use**:
- To build debug/release APKs
- To run automated tests
- To sign and prepare releases
- To troubleshoot build issues

**Common commands**:
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew lint                   # Run lint checks
./gradlew assembleRelease        # Build release APK
./gradlew clean                  # Clean build artifacts
```

**Agent helps with**:
- Explaining build failures
- Optimizing build times
- Handling signing issues
- Managing dependencies
- Automating release distribution

### 4. Documentation Generator
**Purpose**: Generate and maintain documentation

**When to use**:
- To document new APIs
- To create architecture diagrams
- To update README and guides
- To generate API documentation

**Example**:
```
Document the S3Repository interface with KDoc comments
```

**Agent creates**:
- KDoc comments for public APIs
- Architecture diagrams and flow charts
- README updates
- Setup and troubleshooting guides
- Code examples

### 5. Architecture Advisor
**Purpose**: Guide architectural decisions

**When to use**:
- Before implementing major features
- When refactoring code
- To evaluate design patterns
- To assess technical debt

**Example**:
```
I need to add real-time file sync. What architectural changes would you recommend?
```

**Agent evaluates**:
- Current architecture fit
- Scalability implications
- Design pattern recommendations
- Performance considerations
- Security implications
- Maintenance costs

## Workflow Integration

### Development Workflow
```
1. Write code
   └─> Code Quality Reviewer: Check quality
       └─> Pass? Continue : Fix issues
2. Write tests
   └─> Kotlin Unit Test Builder: Enhance coverage
3. Run automated checks
   └─> Android Build Deployer: Build & test
4. Commit & push
   └─> GitHub Actions: Automated CI/CD
5. Documentation
   └─> Documentation Generator: Update docs
```

### Pull Request Workflow
```
1. Open PR
   └─> GitHub Actions: Build & test
   └─> Code Quality Reviewer: Code review
   └─> Lint checks: Pass/fail
2. Review feedback
   └─> Make changes
   └─> Push updates
3. Approval
   └─> Architecture Advisor: Architecture review
   └─> Merge to main
```

### Release Workflow
```
1. Prepare release
   └─> Update version & changelog
   └─> Create release branch
2. Build & test
   └─> Android Build Deployer: Build release APK
   └─> All tests: Must pass
3. Sign & package
   └─> Sign APK with release keystore
   └─> Generate release bundle
4. Distribute
   └─> Create GitHub release
   └─> Upload to Play Store (optional)
```

## Quick Start

### Initial Setup
1. Clone repository
2. Configure AWS credentials in app settings
3. Install emulator or connect device
4. Run `./gradlew assembleDebug` to verify build

### Development
1. Make code changes
2. Run `./gradlew test` to verify
3. Use agents to review and enhance code
4. Create PR with changes

### Testing
1. Write unit tests
2. Ask Kotlin Unit Test Builder for additional coverage
3. Run `./gradlew test` locally
4. Push PR to trigger GitHub Actions tests

### Building
1. For debug: `./gradlew assembleDebug`
2. For release: `./gradlew assembleRelease`
3. Install with: `./gradlew installDebug`

## Agent Communication

### How to Invoke Agents

**Explicit invocation**:
```
Can you use the Kotlin Unit Test Builder to create tests for this function?
```

**Implicit trigger** (agent auto-detects):
```
I just wrote a new S3Repository method called deleteFile()
```
→ Agent automatically suggests writing tests

**Direct requests**:
- "Review this code" → Code Quality Reviewer
- "Document this API" → Documentation Generator
- "How should I architect this feature?" → Architecture Advisor
- "Build the release APK" → Android Build Deployer

### Agent Output

Agents provide:
1. **Analysis**: What they found and why
2. **Recommendations**: Specific actions to take
3. **Examples**: Code snippets and guidance
4. **Next Steps**: What to do next

## Quality Standards

### Code Quality
- All public APIs must have KDoc comments
- Minimum 70% test coverage
- No security vulnerabilities
- Follow Kotlin style guide

### Testing
- Write tests for new functionality
- Test happy path and error cases
- Use meaningful test names
- Tests must be independent and deterministic

### Documentation
- Update README for user-facing changes
- Document architectural decisions
- Include code examples
- Maintain CHANGELOG.md

### Architecture
- Maintain layered separation (Data/Service/UI)
- Use repositories for data access
- Use services for background work
- Keep business logic out of UI

## Common Tasks

### Add a New Feature
1. Design architecture with Architecture Advisor
2. Implement feature
3. Request Code Quality Review
4. Write tests (with Kotlin Unit Test Builder)
5. Update documentation
6. Create PR for review
7. Deploy with Android Build Deployer

### Fix a Bug
1. Create failing test that reproduces bug
2. Fix the bug
3. Verify test passes
4. Code Quality Review
5. Create PR with fix

### Refactor Code
1. Identify refactoring target
2. Get Architecture Advisor guidance
3. Implement changes
4. Run tests: `./gradlew test`
5. Code Quality Review
6. Update documentation if needed
7. Create PR

### Prepare Release
1. Update version in build.gradle.kts
2. Update CHANGELOG.md
3. Create PR with version bump
4. Merge to main
5. Create GitHub release
6. Android Build Deployer handles APK/Bundle signing
7. Distribute (Play Store, GitHub, etc.)

## Troubleshooting

### Build Failures
```bash
# Clean build
./gradlew clean

# Full rebuild with all checks
./gradlew build
```

### Test Failures
```bash
# Run specific test
./gradlew test --tests "com.example.TestClass"

# Run with debug output
./gradlew test --info
```

### Dependency Issues
```bash
# View dependency tree
./gradlew dependencies

# Update dependencies
./gradlew dependencyUpdates
```

### Gradle Cache Issues
```bash
# Clear Gradle cache
rm -rf ~/.gradle/caches
./gradlew build --refresh-dependencies
```

## Next Steps

1. **Get familiar with agents**: Try asking each agent a question
2. **Set up your environment**: Configure AWS credentials in the app
3. **Make your first change**: Use agents to review and enhance it
4. **Join the workflow**: Follow development workflow in PRs
5. **Learn patterns**: Observe how agents guide development

For more information, see:
- `CLAUDE.md` - Project overview and architecture
- `.claude/agents.yaml` - Agent configuration
- `.claude/project-automation.yaml` - Automation setup
- README.md - User-facing documentation
