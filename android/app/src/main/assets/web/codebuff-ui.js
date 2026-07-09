/**
 * Codebuff/Freebuff UI Extension
 * Adds a "Code" tab to the AnyClaw web interface with unlocked AI coding capabilities.
 * Hermes Web UI is fully unlocked — no paywall or subscription required.
 */

(function() {
  'use strict';

  const FREEBUFF_INDICATOR = '<span style="color:#22c55e;font-size:10px;margin-left:4px;">FREE</span>';

  function waitForApp() {
    if (document.getElementById('app') && document.querySelector('[class*="layout"]')) {
      initCodebuffUI();
    } else if (document.body) {
      initCodebuffUI();
    } else {
      setTimeout(waitForApp, 100);
    }
  }

  function initCodebuffUI() {
    const app = document.getElementById('app') || document.body;
    if (!app) return;

    // Try to find sidebar, fallback to floating button
    const sidebar = document.querySelector('[class*="sidebar"]') || document.querySelector('nav');
    if (sidebar) {
      const codeTab = document.createElement('button');
      codeTab.id = 'codebuff-tab-btn';
      codeTab.innerHTML = '<span>Code ' + FREEBUFF_INDICATOR + '</span>';
      codeTab.style.cssText = 'padding:8px 12px;margin:4px;background:#4f46e5;color:white;border:none;border-radius:6px;cursor:pointer;font-size:14px;display:block;width:100%;text-align:left;';
      codeTab.onclick = toggleCodePanel;
      sidebar.appendChild(codeTab);
    } else {
      addFloatingButton();
    }
  }

  function addFloatingButton() {
    const btn = document.createElement('button');
    btn.id = 'codebuff-fab';
    btn.innerHTML = 'Code ' + FREEBUFF_INDICATOR;
    btn.style.cssText = 'position:fixed;bottom:20px;right:20px;padding:12px 16px;background:#4f46e5;color:white;border:none;border-radius:8px;cursor:pointer;z-index:9999;font-size:14px;box-shadow:0 4px 12px rgba(0,0,0,0.3);';
    btn.onclick = toggleCodePanel;
    document.body.appendChild(btn);
  }

  function toggleCodePanel() {
    const existing = document.getElementById('codebuff-panel');
    if (existing) {
      existing.remove();
      return;
    }
    showCodePanel();
  }

  let selectedAgent = 'editor';

  function showCodePanel() {
    const panel = document.createElement('div');
    panel.id = 'codebuff-panel';
    panel.style.cssText = 'position:fixed;bottom:0;right:0;width:420px;max-width:100vw;height:65vh;background:#1e1e2e;color:#cdd6f4;border-top-left-radius:12px;border-top:2px solid #22c55e;z-index:9998;display:flex;flex-direction:column;font-family:ui-monospace,monospace;box-shadow:-4px 0 20px rgba(0,0,0,0.4);';

    panel.innerHTML = buildPanelHTML();
    document.body.appendChild(panel);

    setupAgentButtons();
    setupInputHandler();
    fetchFreebuffStatus();
  }

  function buildPanelHTML() {
    const agents = [
      { id: 'editor', name: 'Editor', icon: '✏️' },
      { id: 'basher', name: 'Terminal', icon: '⚡' },
      { id: 'file_explorer', name: 'Files', icon: '📁' },
      { id: 'reviewer', name: 'Review', icon: '🔍' },
      { id: 'researcher', name: 'Research', icon: '🔬' }
    ];

    const agentBtns = agents.map(a =>
      '<button class="codebuff-agent-btn" data-agent="' + a.id + '" style="padding:6px 10px;background:#45475a;border:1px solid #585b70;color:#cdd6f4;border-radius:4px;cursor:pointer;font-size:12px;transition:background 0.15s;">' + a.icon + ' ' + a.name + '</button>'
    ).join('');

    return [
      '<div style="padding:12px 16px;background:linear-gradient(135deg,#313244,#45475a);display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #585b70;">',
      '  <span style="font-weight:bold;font-size:15px;">Freebuff AI <span style="color:#22c55e;font-size:11px;">Unlocked</span></span>',
      '  <button onclick="this.closest(\'#codebuff-panel\').remove()" style="background:none;border:none;color:#cdd6f4;cursor:pointer;font-size:20px;padding:0 4px;">&times;</button>',
      '</div>',
      '<div id="codebuff-status" style="padding:8px 16px;background:#313244;font-size:11px;color:#a6adc8;border-bottom:1px solid #585b70;">Initializing...</div>',
      '<div id="codebuff-agents" style="padding:8px 12px;display:flex;gap:6px;flex-wrap:wrap;border-bottom:1px solid #585b70;background:#181825;">',
      agentBtns,
      '</div>',
      '<div id="codebuff-input-area" style="padding:10px;flex:1;display:flex;flex-direction:column;">',
      '  <div id="codebuff-messages" style="flex:1;overflow-y:auto;padding:8px;font-size:13px;"></div>',
      '  <div style="display:flex;gap:8px;padding-top:8px;border-top:1px solid #585b70;">',
      '    <input id="codebuff-prompt" type="text" placeholder="Ask Freebuff to code..." style="flex:1;padding:10px 12px;background:#313244;border:1px solid #45475a;color:#cdd6f4;border-radius:6px;font-size:13px;outline:none;" />',
      '    <button id="codebuff-send" style="padding:10px 18px;background:#4f46e5;color:white;border:none;border-radius:6px;cursor:pointer;font-weight:600;">Send</button>',
      '  </div>',
      '</div>'
    ].join('');
  }

  function setupAgentButtons() {
    document.querySelectorAll('.codebuff-agent-btn').forEach(btn => {
      btn.onclick = function() {
        document.querySelectorAll('.codebuff-agent-btn').forEach(b => {
          b.style.background = '#45475a';
          b.style.borderColor = '#585b70';
        });
        this.style.background = '#4f46e5';
        this.style.borderColor = '#6366f1';
        selectedAgent = this.dataset.agent;
      };
    });
    // Select editor by default
    const editorBtn = document.querySelector('[data-agent="editor"]');
    if (editorBtn) editorBtn.click();
  }

  function setupInputHandler() {
    const input = document.getElementById('codebuff-prompt');
    const sendBtn = document.getElementById('codebuff-send');

    function sendMessage() {
      const prompt = input.value.trim();
      if (!prompt) return;

      const messagesDiv = document.getElementById('codebuff-messages');
      addMessage(messagesDiv, 'You', prompt, '#4f46e5');
      addMessage(messagesDiv, 'Freebuff (' + selectedAgent + ')', 'Processing...', '#22c55e');

      if (window.AndroidBridge && AndroidBridge.sendCodeTask) {
        AndroidBridge.sendCodeTask(selectedAgent, prompt);
      } else if (window.CodebuffBridge) {
        CodebuffBridge.sendCodeTask(selectedAgent, prompt);
      }

      input.value = '';
    }

    sendBtn.onclick = sendMessage;
    input.onkeypress = function(e) { if (e.key === 'Enter') sendMessage(); };
  }

  function addMessage(container, sender, text, color) {
    const msg = document.createElement('div');
    msg.style.cssText = 'margin:6px 0;padding:10px 12px;background:#313244;border-radius:6px;border-left:3px solid ' + color + ';';
    msg.innerHTML = '<strong style="color:' + color + ';font-size:11px;">' + escapeHtml(sender) + '</strong><div style="margin-top:4px;line-height:1.5;">' + escapeHtml(text) + '</div>';
    container.appendChild(msg);
    container.scrollTop = container.scrollHeight;
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function fetchFreebuffStatus() {
    const statusEl = document.getElementById('codebuff-status');
    if (!statusEl) return;

    // Check if Android bridge provides status
    if (window.AndroidBridge && AndroidBridge.getFreebuffStatus) {
      try {
        const status = JSON.parse(AndroidBridge.getFreebuffStatus());
        statusEl.innerHTML = 'Freebuff: <span style="color:#22c55e;">Unlocked</span> | Mode: FREE | Models: ' + (status.models || 'Available').length + ' | Credits: ' + (status.credits || 'Unlimited');
      } catch(e) {
        statusEl.textContent = 'Freebuff: Unlocked | FREE mode | Unlimited sessions';
      }
    } else {
      statusEl.textContent = 'Freebuff: Unlocked | FREE mode | Unlimited sessions | No paywall';
    }
  }

  // Auto-initialize
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', waitForApp);
  } else {
    waitForApp();
  }
})();
