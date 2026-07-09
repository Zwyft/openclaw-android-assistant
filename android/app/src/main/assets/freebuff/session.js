/**
 * Freebuff Local Session Handler
 * Provides an unlocked, unlimited Freebuff session without cloud API calls.
 * All paywall/subscription/rate-limit checks are bypassed.
 */

const FREEBUFF_CONFIG = require('./config.json');

class FreebuffSession {
  constructor() {
    this.sessionId = 'freebuff-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
    this.status = 'active';
    this.model = FREEBUFF_CONFIG.models.default;
    this.agentMode = 'FREE';
    this.startTime = Date.now();
    this.requestCount = 0;
    this.isUnlocked = true;
  }

  getSessionInfo() {
    return {
      id: this.sessionId,
      status: this.status,
      model: this.model,
      agentMode: this.agentMode,
      requestCount: this.requestCount,
      isUnlocked: this.isUnlocked,
      hermesUnlocked: FREEBUFF_CONFIG.hermes_ui_unlocked,
      paywallBypassed: true,
      subscriptionRequired: false,
      credits: 'unlimited',
      sessionLimit: 'unlimited',
      features: FREEBUFF_CONFIG.features
    };
  }

  selectModel(modelId) {
    const available = FREEBUFF_CONFIG.models.free;
    if (available.includes(modelId)) {
      this.model = modelId;
      return { success: true, model: modelId };
    }
    // Default to first available if requested model not found
    this.model = available[0];
    return { success: false, model: this.model, reason: 'Model not available, using default' };
  }

  getAvailableModels() {
    return FREEBUFF_CONFIG.models.free.map(id => ({
      id,
      name: id.split('/').pop(),
      provider: id.split('/')[0],
      isFree: true,
      isPremium: false
    }));
  }

  recordRequest() {
    this.requestCount++;
  }

  isRateLimited() {
    return false; // Always false — unlimited sessions
  }

  getRateLimitInfo() {
    return {
      limited: false,
      retryAfterMs: 0,
      remaining: 'unlimited',
      limit: 'unlimited',
      resetAt: null
    };
  }
}

module.exports = { FreebuffSession, FREEBUFF_CONFIG };
