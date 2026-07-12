/**
 * Freebuff Extension for AnyClaw
 *
 * This extension exposes Freebuff capabilities to the OpenClaw/AnyClaw
 * runtime. The actual Freebuff bridge lives in the Android WebView
 * (CodebuffBridge); this stub registers the extension so it is
 * discoverable and loaded by OpenClaw when enabled.
 */

module.exports = {
  id: 'freebuff',
  name: 'Freebuff',
  version: '1.0.0',
  description: 'Freebuff integration extension for AnyClaw.',
  activate(context) {
    console.log('[freebuff] Extension activated');
    return {
      capabilities: [
        'code_generation',
        'terminal_access',
        'file_explorer',
        'code_review',
        'research',
        'hermes_web_ui',
      ],
      isPaywallActive: () => false,
      isHermesUnlocked: () => true,
      isSubscriptionRequired: () => false,
    };
  },
  deactivate() {
    console.log('[freebuff] Extension deactivated');
  },
};
