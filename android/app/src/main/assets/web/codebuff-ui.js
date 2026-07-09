/**
 * Codebuff UI Extension
 * Adds a "Code" tab to the AnyClaw web interface with agent selection and coding capabilities.
 */

(function() {
  'use strict';

  // Wait for DOM to be ready
  function waitForApp() {
    if (document.getElementById('app') && document.querySelector('[class*="layout"]')) {
      initCodebuffUI();
    } else {
      setTimeout(waitForApp, 100);
    }
  }

  function initCodebuffUI() {
    const app = document.getElementById('app');
    if (!app) return;

    // Add Code tab button to sidebar
    const sidebar = document.querySelector('[class*="sidebar"]') || document.querySelector('nav');
    if (!sidebar) {
      console.log('Codebuff: Sidebar not found, adding floating button');
      addFloatingButton();
      return;
    }

    // Add tab button
    const codeTab = document.createElement('button');
    codeTab.id = 'codebuff-tab-btn';
    codeTab.className = 'sidebar-tab-btn';
    codeTab.innerHTML = '<span>Code</span>';
    codeTab.style.cssText = 'padding:8px 12px;margin:4px;background:#4f46e5;color:white;border:none;border-radius:6px;cursor:pointer;font-size:14px;';
    codeTab.onclick = showCodePanel;
    sidebar.appendChild(codeTab);
  }

  function addFloatingButton() {
    const btn = document.createElement('button');
    btn.id = 'codebuff-fab';
    btn.innerHTML = 'Code';
    btn.style.cssText = 'position:fixed;bottom:20px;right:20px;padding:12px 16px;background:#4f46e5;color:white;border:none;border-radius:8px;cursor:pointer;z-index:9999;font-size:14px;box-shadow:0 4px 12px rgba(0,0,0,0.3);';
    btn.onclick = showCodePanel;
    document.body.appendChild(btn);
  }

  function showCodePanel() {
    // Close existing panel if open
    const existing = document.getElementById('codebuff-panel');
    if (existing) {
      existing.remove();
      return;
    }

    // Create code panel
    const panel = document.createElement('div');
    panel.id = 'codebuff-panel';
    panel.style.cssText = 'position:fixed;bottom:0;right:0;width:400px;max-width:100vw;height:60vh;background:#1e1e2e;color:#cdd6f4;border-top:1px solid #313244;z-index:9998;display:flex;flex-direction:column;font-family:monospace;';
    
    panel.innerHTML = `
      <div style="padding:12px;background:#313244;display:flex;justify-content:space-between;align-items:center;">
        <span style="font-weight:bold;">Codebuff AI - Free</span>
        <button onclick="document.getElementById('codebuff-panel').remove()" style="background:none;border:none;color:#cdd6f4;cursor:pointer;font-size:18px;">x</button>
      </div>
      <div id="codebuff-agents" style="padding:8px;display:flex;gap:8px;flex-wrap:wrap;border-bottom:1px solid #313244;">
        <button class="codebuff-agent-btn" data-agent="editor" style="padding:6px 12px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;">Editor</button>
        <button class="codebuff-agent-btn" data-agent="basher" style="padding:6px 12px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;">Terminal</button>
        <button class="codebuff-agent-btn" data-agent="file_explorer" style="padding:6px 12px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;">Files</button>
        <button class="codebuff-agent-btn" data-agent="reviewer" style="padding:6px 12px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;">Review</button>
        <button class="codebuff-agent-btn" data-agent="researcher" style="padding:6px 12px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;">Research</button>
      </div>
      <div id="codebuff-input-area" style="padding:8px;flex:1;display:flex;flex-direction:column;">
        <div id="codebuff-messages" style="flex:1;overflow-y:auto;padding:8px;font-size:13px;"></div>
        <div style="display:flex;gap:8px;padding:8px 0;">
          <input id="codebuff-prompt" type="text" placeholder="Ask AI to code..." style="flex:1;padding:8px;background:#313244;border:1px solid #45475a;color:#cdd6f4;border-radius:4px;font-size:13px;" />
          <button id="codebuff-send" style="padding:8px 16px;background:#4f46e5;color:white;border:none;border-radius:4px;cursor:pointer;">Send</button>
        </div>
      </div>
    `;
    
    document.body.appendChild(panel);
    
    // Set up event listeners
    setupAgentButtons();
    setupInputHandler();
  }

  let selectedAgent = 'editor';

  function setupAgentButtons() {
    document.querySelectorAll('.codebuff-agent-btn').forEach(btn => {
      btn.onclick = () => {
        document.querySelectorAll('.codebuff-agent-btn').forEach(b => b.style.background = '#45475a');
        btn.style.background = '#4f46e5';
        selectedAgent = btn.dataset.agent;
      };
    });
  }

  function setupInputHandler() {
    const input = document.getElementById('codebuff-prompt');
    const sendBtn = document.getElementById('codebuff-send');
    
    function sendMessage() {
      const prompt = input.value.trim();
      if (!prompt) return;
      
      const messagesDiv = document.getElementById('codebuff-messages');
      messagesDiv.innerHTML += '<div style="margin:4px 0;padding:8px;background:#313244;border-radius:4px;"><strong>You:</strong> ' + escapeHtml(prompt) + '</div>';
      messagesDiv.innerHTML += '<div style="margin:4px 0;padding:8px;background:#45475a;border-radius:4px;"><strong>AI (' + selectedAgent + '):</strong> Processing...</div>';
      messagesDiv.scrollTop = messagesDiv.scrollHeight;
      
      // Send to native Android bridge
      if (window.AndroidBridge && AndroidBridge.sendCodeTask) {
        AndroidBridge.sendCodeTask(selectedAgent, prompt);
      }
      
      input.value = '';
    }
    
    sendBtn.onclick = sendMessage;
    input.onkeypress = (e) => { if (e.key === 'Enter') sendMessage(); };
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Start initialization
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', waitForApp);
  } else {
    waitForApp();
  }
})();
