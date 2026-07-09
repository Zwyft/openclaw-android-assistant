/**
 * Codebuff Agent Launcher
 * Spawns and manages Codebuff AI agents within the embedded Linux environment.
 * Communicates via stdin/stdout using JSON-RPC protocol.
 */

const CODEBUFF_DIR = process.env.CODEBUFF_DIR || '/data/data/com.codex.mobile/files/usr/codebuff';
const AGENT_REGISTRY = CODEBUFF_DIR + '/agents';

// Available agent definitions
const AGENTS = {
  editor: {
    id: 'editor',
    displayName: 'Code Editor',
    description: 'Generate, edit, and refactor code across files',
    model: 'openai/gpt-5-nano',
  },
  basher: {
    id: 'basher',
    displayName: 'Terminal',
    description: 'Execute shell commands and scripts',
    model: 'openai/gpt-5-nano',
  },
  file_explorer: {
    id: 'file_explorer',
    displayName: 'File Explorer',
    description: 'Browse, search, and analyze project files',
    model: 'openai/gpt-5-nano',
  },
  reviewer: {
    id: 'reviewer',
    displayName: 'Code Reviewer',
    description: 'Review code for quality, security, and best practices',
    model: 'openai/gpt-5-nano',
  },
  researcher: {
    id: 'researcher',
    displayName: 'Researcher',
    description: 'Research documentation and gather information',
    model: 'openai/gpt-5-nano',
  },
};

const runningAgents = new Map();
let messageId = 0;
let buffer = '';

function sendResponse(id, result) {
  const response = JSON.stringify({ id, result, jsonrpc: '2.0' });
  process.stdout.write(response + '\n');
}

function sendError(id, code, message) {
  const response = JSON.stringify({
    id,
    error: { code, message },
    jsonrpc: '2.0',
  });
  process.stdout.write(response + '\n');
}

function handleRequest(request) {
  const { id, method, params } = request;
  
  switch (method) {
    case 'agent/list':
      sendResponse(id, Object.values(AGENTS));
      break;
      
    case 'agent/start': {
      const { agentId, prompt, cwd } = params;
      const agent = AGENTS[agentId];
      if (!agent) {
        return sendError(id, -32601, 'Agent not found: ' + agentId);
      }
      
      const uniqueId = 'agent-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8);
      
      // For now, just track the agent as started
      runningAgents.set(uniqueId, { agentId, prompt, cwd: cwd || process.cwd(), status: 'running' });
      
      sendResponse(id, { agentId: uniqueId, status: 'started' });
      break;
    }
    
    case 'agent/send': {
      const { agentId, message } = params;
      const running = runningAgents.get(agentId);
      if (!running) {
        return sendError(id, -32602, 'Agent not running: ' + agentId);
      }
      // Process message and generate response
      running.lastMessage = message;
      sendResponse(id, { sent: true, agentId });
      break;
    }
    
    case 'agent/stop': {
      const { agentId } = params;
      runningAgents.delete(agentId);
      sendResponse(id, { stopped: true });
      break;
    }
    
    case 'agent/status': {
      const { agentId } = params;
      const running = runningAgents.get(agentId);
      if (!running) {
        return sendError(id, -32602, 'Agent not found: ' + agentId);
      }
      sendResponse(id, { status: running.status, agentId });
      break;
    }
    
    case 'agent/all_status':
      sendResponse(id, Array.from(runningAgents.entries()).map(([id, data]) => ({ id, ...data })));
      break;
    
    default:
      sendError(id, -32601, 'Method not found: ' + method);
  }
}

// Handle stdin
process.stdin.setEncoding('utf8');

process.stdin.on('data', (chunk) => {
  buffer += chunk;
  const lines = buffer.split('\n');
  buffer = lines.pop() || '';
  
  for (const line of lines) {
    if (line.trim()) {
      try {
        const request = JSON.parse(line);
        handleRequest(request);
      } catch (e) {
        sendError(null, -32700, 'Parse error: ' + e.message);
      }
    }
  }
});

// Send ready signal to stderr (so it doesn't interfere with JSON-RPC)
console.error(JSON.stringify({ type: 'ready', agents: Object.keys(AGENTS) }));
