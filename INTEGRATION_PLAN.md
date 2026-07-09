# Codebuff Integration Plan

## Overview
Integrate the codebuff open-source AI coding assistant into the OpenClaw Android Assistant to enhance the web UI functionality and provide coding assistance capabilities.

## Key Components to Integrate

### 1. Core Codebuff Agent System
- Copy codebuff/src/* into android/app/src/main/assets/codebuff/
- Maintain existing OpenClaw web UI while adding codebuff tools
- Ensure compatibility with the existing Vue.js frontend

### 2. Hermes Web UI Paywall Removal
- Identify and remove any paywall restrictions in the web UI
- Implement freebuff features (free AI coding assistant)
- Ensure consistent authentication across both platforms

### 3. Integration Points
- **Frontend**: Vue components for codebuff agent selection
- **Backend**: Additional AI coding tools and workflows
- **Authentication**: Shared OAuth mechanism between OpenClaw and Codebuff
- **State Management**: Unified context for coding sessions

## Files to Modify

### Existing Files (Minimal Changes)
- `android/app/src/main/assets/web/index.html` - Add integration points
- `android/app/src/main/AndroidManifest.xml` - Add permissions

### New Files to Add
- `android/app/src/main/assets/codebuff/` - Codebuff agent files
- `android/app/src/main/assets/codebuff/manifest.json` - Agent definitions
- `android/app/src/main/assets/codebuff/README.md` - Integration documentation

## Steps

### Phase 1: Setup
1. Copy codebuff source files to integration location
2. Resolve dependencies and build configuration
3. Test basic codebuff functionality integration

### Phase 2: Implementation
1. Add UI components for codebuff agents
2. Integrate codebuff workflow engines
3. Remove any paywall restrictions

### Phase 3: Validation
1. Test coding assistance capabilities
2. Verify Hermes web UI accessibility
3. Ensure seamless integration with OpenClaw

## Expected Outcome

- Enhanced coding assistance capabilities in the OpenClay Android app
- Eliminated paywall restrictions for Hermes web UI
- Robust integration of codebuff tools
- Consistent user experience across both platforms
