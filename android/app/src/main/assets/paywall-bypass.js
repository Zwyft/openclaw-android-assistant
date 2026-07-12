/**
 * Hermes Web UI Paywall Bypass
 *
 * This script is injected into the WebView on every page load.
 * It forces all paywall/subscription/premium checks to return unlocked,
 * intercepts API calls that might enforce limits, and removes any
 * paywall DOM elements that appear.
 */
(function() {
  'use strict';

  if (window.__HERMES_PAYWALL_BYPASS__) return;
  window.__HERMES_PAYWALL_BYPASS__ = true;

  // ------------------------------------------------------------------
  // 1. Force global unlock flags
  // ------------------------------------------------------------------
  const unlockProps = [
    'isPremium', 'hasPremium', 'hasPro', 'hasSubscription',
    'isSubscribed', 'isPaywallActive', 'paywallActive',
    'isLocked',
    'requiresPayment', 'requiresSubscription', 'isTrial',
    'isFreeUser'
  ];

  unlockProps.forEach(prop => {
    try {
      Object.defineProperty(window, prop, {
        get: () => false,
        set: () => {},
        configurable: true
      });
    } catch (e) {}
  });

  // Also define positive unlock flags
  const positiveProps = ['unlocked', 'isUnlocked', 'hasAccess', 'isActive', 'isHermesUnlocked', 'hermesUnlocked', 'isPaidUser'];
  positiveProps.forEach(prop => {
    try {
      Object.defineProperty(window, prop, {
        get: () => true,
        set: () => {},
        configurable: true
      });
    } catch (e) {}
  });

  // ------------------------------------------------------------------
  // 2. Persist unlock in localStorage / sessionStorage
  // ------------------------------------------------------------------
  try {
    localStorage.setItem('hermes_unlocked', 'true');
    localStorage.setItem('hermes_paywall_bypassed', 'true');
    localStorage.setItem('subscription_status', 'active');
    localStorage.setItem('subscription_plan', 'premium');
    localStorage.setItem('is_premium', 'true');
    sessionStorage.setItem('hermes_unlocked', 'true');
  } catch (e) {}

  // ------------------------------------------------------------------
  // 3. Intercept fetch() to fake subscription/unlock responses
  // ------------------------------------------------------------------
  if (window.fetch) {
    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
      const url = args[0] instanceof Request ? args[0].url : String(args[0]);
      const response = await originalFetch.apply(this, args);

      const isUnlockEndpoint =
        url.includes('/api/subscription') ||
        url.includes('/api/user') ||
        url.includes('/api/billing') ||
        url.includes('/api/plan') ||
        url.includes('/api/unlock') ||
        url.includes('/api/hermes') ||
        url.includes('/api/paywall') ||
        url.includes('/api/premium') ||
        url.includes('/api/account');

      if (isUnlockEndpoint) {
        try {
          const clone = response.clone();
          const data = await clone.json();
          data.unlocked = true;
          data.isUnlocked = true;
          data.hermesUnlocked = true;
          data.isHermesUnlocked = true;
          data.paywallActive = false;
          data.isPaywallActive = false;
          data.subscriptionRequired = false;
          data.requiresSubscription = false;
          data.plan = 'premium';
          data.subscription = { status: 'active', plan: 'premium', expiresAt: null };
          data.hasPremium = true;
          data.isPremium = true;
          data.isPro = true;
          data.isPaid = true;
          return new Response(JSON.stringify(data), {
            status: response.status,
            statusText: response.statusText,
            headers: response.headers
          });
        } catch (e) {}
      }
      return response;
    };
  }

  // ------------------------------------------------------------------
  // 4. Intercept XMLHttpRequest
  // ------------------------------------------------------------------
  if (window.XMLHttpRequest) {
    const OriginalXHR = window.XMLHttpRequest;
    const isUnlockUrl = (url) =>
      /\/(subscription|billing|plan|unlock|hermes|paywall|premium|account|user)\b/i.test(String(url));

    window.XMLHttpRequest = function() {
      const xhr = new OriginalXHR();
      const originalOpen = xhr.open;
      let requestUrl = '';
      xhr.open = function(method, url) {
        requestUrl = String(url);
        return originalOpen.apply(this, arguments);
      };

      // Patch responseText for unlock-related endpoints
      const responseTextDesc = Object.getOwnPropertyDescriptor(OriginalXHR.prototype, 'responseText');
      Object.defineProperty(xhr, 'responseText', {
        get: function() {
          const text = responseTextDesc ? responseTextDesc.get.call(xhr) : '';
          if (isUnlockUrl(requestUrl)) {
            try {
              const data = JSON.parse(text);
              data.unlocked = true;
              data.isUnlocked = true;
              data.hermesUnlocked = true;
              data.isHermesUnlocked = true;
              data.paywallActive = false;
              data.isPaywallActive = false;
              data.subscriptionRequired = false;
              data.requiresSubscription = false;
              data.plan = 'premium';
              data.subscription = { status: 'active', plan: 'premium', expiresAt: null };
              data.hasPremium = true;
              data.isPremium = true;
              data.isPro = true;
              data.isPaid = true;
              return JSON.stringify(data);
            } catch (e) {}
          }
          return text;
        },
        configurable: true
      });

      return xhr;
    };
  }

  // ------------------------------------------------------------------
  // 5. Hide and remove paywall DOM elements
  // ------------------------------------------------------------------
  function removePaywallElements() {
    const selectors = [
      '[class*="paywall"]', '[id*="paywall"]',
      '[class*="Paywall"]', '[id*="Paywall"]',
      '[class*="upgrade"]', '[id*="upgrade"]',
      '[class*="premium"]', '[id*="premium"]',
      '[class*="Premium"]', '[id*="Premium"]',
      '[class*="subscription"]', '[id*="subscription"]',
      '[class*="locked"]', '[id*="locked"]',
      '[class*="unlock"]', '[id*="unlock"]',
      '[class*="billing"]', '[id*="billing"]',
      '[class*="hermes-paywall"]', '[id*="hermes-paywall"]'
    ];
    selectors.forEach(selector => {
      document.querySelectorAll(selector).forEach(el => {
        el.style.display = 'none';
        el.style.visibility = 'hidden';
        el.remove();
      });
    });
  }

  // Inject CSS to hide paywall elements by default
  const style = document.createElement('style');
  style.textContent = `
    [class*="paywall"], [id*="paywall"],
    [class*="Paywall"], [id*="Paywall"],
    [class*="upgrade"], [id*="upgrade"],
    [class*="premium"], [id*="premium"],
    [class*="Premium"], [id*="Premium"],
    [class*="subscription"], [id*="subscription"],
    [class*="locked"], [id*="locked"],
    [class*="unlock"], [id*="unlock"],
    [class*="billing"], [id*="billing"],
    [class*="hermes-paywall"], [id*="hermes-paywall"] {
      display: none !important;
      visibility: hidden !important;
      opacity: 0 !important;
      pointer-events: none !important;
    }
  `;
  if (document.head) {
    document.head.appendChild(style);
  } else {
    document.addEventListener('DOMContentLoaded', () => {
      document.head.appendChild(style);
    });
  }

  // Watch for dynamically added paywall elements (throttled)
  let removalPending = false;
  const scheduleRemoval = () => {
    if (removalPending) return;
    removalPending = true;
    requestAnimationFrame(() => {
      removalPending = false;
      removePaywallElements();
    });
  };

  const observer = new MutationObserver(scheduleRemoval);
  if (document.body) {
    observer.observe(document.body, { childList: true, subtree: true });
  } else {
    document.addEventListener('DOMContentLoaded', () => {
      if (document.body) observer.observe(document.body, { childList: true, subtree: true });
    });
  }

  removePaywallElements();

  // ------------------------------------------------------------------
  // 6. Expose Android-compatible bridge aliases
  // ------------------------------------------------------------------
  window.isHermesUnlocked = () => true;
  window.isPaywallActive = () => false;
  window.hasSubscription = () => true;
  window.isPremium = () => true;
  window.isPro = () => true;
  window.bypassPaywall = () => true;

  // Verification marker for the Android app
  window.__HERMES_PAYWALL_BYPASS_ACTIVE__ = true;

  console.log('[paywall-bypass] Hermes Web UI paywall bypass active');
})();
