/**
 * AnyClaw Settings Manager
 * Handles provider API key configuration without mandatory login.
 */

(function() {
  'use strict';

  window.SettingsManager = {
    providers: {
      openai: { name: 'OpenAI (Codex)', keyPrefix: 'OPENAI_API_KEY' },
      opencode: { name: 'OpenCode', keyPrefix: 'OPENCODE_API_KEY' },
      zen: { name: 'Zen', keyPrefix: 'ZEN_API_KEY' },
      openrouter: { name: 'OpenRouter', keyPrefix: 'OPENROUTER_API_KEY' },
      openapi: { name: 'OpenAPI', keyPrefix: 'OPENAPI_API_KEY' }
    },

    saveProviderKey(providerId, apiKey) {
      try {
        localStorage.setItem('api_key_' + providerId, apiKey);
        this.notifyProviderChange(providerId, true);
        return true;
      } catch (e) {
        console.error('Failed to save key:', e);
        return false;
      }
    },

    getProviderKey(providerId) {
      try {
        return localStorage.getItem('api_key_' + providerId) || null;
      } catch (e) {
        console.error('Failed to get key:', e);
        return null;
      }
    },

    removeProviderKey(providerId) {
      try {
        localStorage.removeItem('api_key_' + providerId);
        this.notifyProviderChange(providerId, false);
        return true;
      } catch (e) {
        return false;
      }
    },

    notifyProviderChange(providerId, isSet) {
      // Notify Android bridge if available
      if (window.AndroidBridge) {
        AndroidBridge.notifyProviderChange(providerId, isSet ? 'connected' : 'disconnected');
      }
    },

    // Check if we have any credentials configured
    hasAnyCredentials() {
      for (const providerId in this.providers) {
        if (this.getProviderKey(providerId)) {
          return true;
        }
      }
      return false;
    },

    // Get all configured providers
    getConfiguredProviders() {
      const list = [];
      for (const providerId in this.providers) {
        const key = this.getProviderKey(providerId);
        if (key) {
          list.push({
            id: providerId,
            name: this.providers[providerId].name,
            connected: true
          });
        }
      }
      return list;
    }
  };
})();
