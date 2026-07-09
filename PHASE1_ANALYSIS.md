# Phase 1: Foundation Analysis

## Current Environment Structure

### Embedded Runtime Components

**1. Core Runtime Files**
- `android/app/src/main/assets/
  ├── bionic-compat.js`
  ├── proxy.js`
  ├── setup-codex.sh`
  ├── web/
  │   ├── index.html`
  │   ├── assets/
  │   └── index-*.js/css`
  └─ (scripts in `android/scripts/` for setup & management)

**2. Backend Management**
- `android/app/src/main/java/com/codex/mobile/`:
  - `CodexServerManager.kt` - Manages Node.js process and services
  - `BootstrapInstaller.kt` - Sets up Termux prefix environment
  - `CodexForegroundService.kt` - App lifecycle management

**3. Android App Structure**
- `android/app/build.gradle.kts` - Module configuration
- Uses **Kotlin** with **Coroutines** and **Retrofit**
- Embedded Linux environment (Termux-derived)
- Self-contained with Node.js, Python, and native binaries

---

## Existing Capabilities

### OpenAI Codex Integration
- ✅ Node.js with `@openai/codex` CLI
- ✅ Web UI for conversational AI
- ✅ Command execution (shell, file operations)
- ✅ Multi-agent support via `codex` app-server

### OpenClaw Features
- ✅ Multi-channel AI assistant support
- ✅ Agent routing
- ✅ Skills and Canvas capabilities
- ✅ Dashboard UI

### Current Limitations
- ❌ No Codebuff/Codegent integration
- ❌ Paywall on Hermes web UI
- ❌ Limited to OpenAI models only
- ❌ No advanced code generation/refactoring tools

---

## Integration Strategy

### 1. Backend Extension

**Current `CodexServerManager.kt` handles:**
```kotlin
// Existing functionality:
- Server process management
- Proxy configuration  
- OpenClaw gateway communication
- Authentication handling
```

**New components to add:**
```kotlin
// Proposed additions:
class CodebuffAgentManager {
    // Codebuff agent spawning and management
    // Tool integration
    // Workflow coordination
}

class UnifiedAgentRouter {
    // Routes requests to appropriate agent
    // Maintains session isolation
    // Handles cross-agent communication
}
```

### 2. Frontend Enhancement

**Current UI Structure:**
- Vue.js web UI in `android/app/src/main/assets/web/`
- Tab-based interface for different features
- Reactive state management

**Proposed UI Changes:**
- Add Code tab alongside existing OpenClaw chat
- Codebuff agent selector with specialized workflows
- Unified session management across all AI providers

### 3. Paywall Removal

**Identify Paywall Code:**
1. Search for `subscription`, `premium`, `unlock`, `paywall` in web assets
2. Check authentication flow for premium feature gates
3
