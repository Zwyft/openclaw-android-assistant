/**
 * Freebuff Local API
 * Replaces cloud-based Freebuff API with local unlocked implementation.
 * No network calls needed — everything runs locally.
 */

const { FreebuffSession, FREEBUFF_CONFIG } = require('./session');

const activeSession = new FreebuffSession();

// Agent definitions for local execution
const AGENTS = {
  editor: {
    id: 'editor',
    name: 'Code Editor',
    type: 'code_generator',
    systemPrompt: 'You are a code editor agent. Help users write, edit, and refactor code.',
    model: 'openai/gpt-5-nano',
    capabilities: ['read_file', 'write_file', 'edit_file', 'search_code']
  },
  basher: {
    id: 'basher',
    name: 'Terminal',
    type: 'code_executor',
    systemPrompt: 'You are a terminal agent. Help users execute shell commands safely.',
    model: 'openai/gpt-5-nano',
    capabilities: ['run_command', 'read_output', 'manage_process']
  },
  file_explorer: {
    id: 'file_explorer',
    name: 'File Explorer',
    type: 'file_browser',
    systemPrompt: 'You are a file explorer agent. Help users navigate and understand their codebase.',
    model: 'openai/gpt-5-nano',
    capabilities: ['list_dir', 'read_file', 'search_files', 'glob']
  },
  reviewer: {
    id: 'reviewer',
    name: 'Code Reviewer',
    type: 'code_reviewer',
    systemPrompt: 'You are a code review agent. Analyze code for quality, security, and best practices.',
    model: 'openai/gpt-5-nano',
    capabilities: ['analyze_code', 'suggest_improvements', 'security_check']
  },
  researcher: {
    id: 'researcher',
    name: 'Researcher',
    type: 'researcher',
    systemPrompt: 'You are a research agent. Help users find documentation and gather information.',
    model: 'openai/gpt-5-nano',
    capabilities: ['search_docs', 'gather_info', 'summarize']
  }
};

// Handle API requests
function handleRequest(request) {
  const { method, params, id } = request;

  switch (method) {
    // Session endpoints
    case 'session/info':
      return { id, result: activeSession.getSessionInfo() };

    case 'session/models':
      return { id, result: activeSession.getAvailableModels() };

    case 'session/select_model':
      return { id, result: activeSession.selectModel(params.modelId) };

    case 'session/rate_limit':
      return { id, result: activeSession.getRateLimitInfo() };

    // Agent endpoints
    case 'agent/list':
      return { id, result: Object.values(AGENTS) };

    case 'agent/start': {
      const { agentId, prompt, cwd } = params;
      const agent = AGENTS[agentId];
      if (!agent) {
        return { id, error: { code: -32601, message: 'Agent not found: ' + agentId } };
      }
      activeSession.recordRequest();
      return {
        id,
        result: {
          agentId: agentId + '-' + Date.now(),
          status: 'started',
          model: agent.model,
          session: activeSession.getSessionInfo()
        }
      };
    }

    case 'agent/send': {
      const { agentId, message } = params;
      activeSession.recordRequest();
      return { id, result: { sent: true, agentId } };
    }

    case 'agent/stop': {
      const { agentId } = params;
      return { id, result: { stopped: true, agentId } };
    }

    case 'agent/status': {
      const { agentId } = params;
      return { id, result: { status: 'active', agentId } };
    }

    case 'agent/all_status':
      return { id, result: { agents: {}, session: activeSession.getSessionInfo() } };

    // Freebuff-specific endpoints
    case 'freebuff/unlock_status':
      return {
        id,
        result: {
          unlocked: true,
          hermesUnlocked: true,
          paywallRemoved: true,
          subscriptionRequired: false,
          credits: 'unlimited',
          message: 'Freebuff is fully unlocked. No paywall restrictions.'
        }
      };

    case 'freebuff/config':
      return { id, result: FREEBUFF_CONFIG };

    // Health check
    case 'ping':
      return { id, result: { pong: true, session: activeSession.sessionId } };

    default:
      return { id, error: { code: -32601, message: 'Method not found: ' + method } };
  }
}

module.exports = { handleRequest, AGENTS, activeSession, FREEBUFF_CONFIG };
