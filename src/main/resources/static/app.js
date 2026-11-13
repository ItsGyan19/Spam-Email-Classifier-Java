/**
 * Frontend behavior for Email Safety Checker
 */

const PRESETS = {
  phishing: "URGENT: Your PayPal account has been temporarily restricted due to suspicious login attempts from Russia. Click here to verify your identity immediately: http://paypal-verify-account-security.com or account will be suspended in 24 hours.",
  lottery: "CONGRATULATIONS! You have been selected as the grand cash prize winner of $2,500,000 in the International EuroMillions Sweepstakes! Wire transfer ready. Reply with your full name, age, and bank details immediately to release funds.",
  crypto: "EARN $1,500 TO $3,000 PER DAY WORKING FROM HOME! Automated Bitcoin AI Trading Bot guarantees 450% weekly return! Claim 5,000 free tokens today: http://btc-ai-millionaire-robot.io",
  work: "Hi team, please find attached the weekly sprint report. Can we reschedule our sync from 2 PM to 3:30 PM today in Conference Room B? Let me know if that works. Best regards, Alex.",
  receipt: "Your flight booking #AA9482 is confirmed. Departure: New York (JFK) at 08:30 AM. Baggage receipts and boarding passes are attached. Thank you for booking with American Airlines."
};

const TOPIC_ICONS = {
  phishing: "🚨",
  lottery: "💰",
  marketing: "🏷️",
  crypto_scam: "📈",
  work: "💼",
  personal: "☕",
  notification: "✈️",
  newsletter: "📰",
  unknown: "📧"
};

document.addEventListener("DOMContentLoaded", () => {
  const emailInput = document.getElementById("email-input");
  const charCount = document.getElementById("char-count");
  const clearBtn = document.getElementById("clear-btn");
  const classifyBtn = document.getElementById("classify-btn");

  const emptyState = document.getElementById("empty-state");
  const resultsPanel = document.getElementById("results-panel");
  const analysisStatus = document.getElementById("analysis-status");

  const verdictBanner = document.getElementById("verdict-banner");
  const verdictIcon = document.getElementById("verdict-icon");
  const verdictTag = document.getElementById("verdict-tag");
  const verdictTitle = document.getElementById("verdict-title");
  const confidenceVal = document.getElementById("confidence-val");

  const hamPct = document.getElementById("ham-pct");
  const spamPct = document.getElementById("spam-pct");
  const probabilityMeter = document.getElementById("probability-meter");

  const topicIcon = document.getElementById("topic-icon");
  const topicText = document.getElementById("topic-text");
  const topicConf = document.getElementById("topic-conf");

  const hCap = document.getElementById("h-cap");
  const hCurrency = document.getElementById("h-currency");
  const hUrls = document.getElementById("h-urls");
  const hExclamation = document.getElementById("h-exclamation");

  const spamWordsList = document.getElementById("spam-words-list");
  const hamWordsList = document.getElementById("ham-words-list");

  const refreshMetricsBtn = document.getElementById("refresh-metrics-btn");
  const benchmarkTbody = document.getElementById("benchmark-tbody");

  // Update text stats
  function updateCounters() {
    const text = emailInput.value;
    const chars = text.length;
    const words = text.trim() ? text.trim().split(/\s+/).length : 0;
    charCount.textContent = `${chars} characters | ${words} words`;
  }

  emailInput.addEventListener("input", updateCounters);

  // Preset Buttons
  document.querySelectorAll(".preset-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const key = btn.dataset.preset;
      if (PRESETS[key]) {
        emailInput.value = PRESETS[key];
        updateCounters();
        performClassification();
      }
    });
  });

  // Clear Button
  clearBtn.addEventListener("click", () => {
    emailInput.value = "";
    updateCounters();
    emptyState.classList.remove("hidden");
    resultsPanel.classList.add("hidden");
    analysisStatus.textContent = "Awaiting input...";
  });

  // Keyboard shortcut: Ctrl+Enter or Cmd+Enter to classify
  emailInput.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      performClassification();
    }
  });

  // Classify Action
  classifyBtn.addEventListener("click", performClassification);

  async function performClassification() {
    const text = emailInput.value.trim();
    if (!text) {
      alert("Please paste or type an email text first.");
      return;
    }

    // Client-side guard: prevent freezing browser or server with gigantic text
    if (text.length > 50000) {
      alert(`Email text is too large (${text.length.toLocaleString()} characters). Maximum allowed limit is 50,000 characters.`);
      return;
    }

    classifyBtn.disabled = true;
    analysisStatus.textContent = "Checking the email...";

    try {
      const response = await fetch("/api/classify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.error || `Server error (${response.status})`);
      }

      const data = await response.json();
      renderResults(data);
    } catch (err) {
      console.error(err);
      analysisStatus.textContent = "Error running classification";
      alert("Classification failed: " + err.message);
    } finally {
      classifyBtn.disabled = false;
    }
  }

  function renderResults(data) {
    emptyState.classList.add("hidden");
    resultsPanel.classList.remove("hidden");
    analysisStatus.textContent = "Analysis complete";

    const isSpam = data.is_spam;

    // Banner styling
    verdictBanner.className = `verdict-banner ${isSpam ? "is-spam" : "is-ham"}`;
    verdictIcon.textContent = isSpam ? "🚨" : "✅";
    verdictTag.textContent = isSpam ? "SPAM DETECTED" : "LEGITIMATE EMAIL (HAM)";
    verdictTitle.textContent = isSpam
      ? "High probability of deceptive or promotional content"
      : "Standard communication / safe email";
    confidenceVal.textContent = `${data.confidence_percent.toFixed(1)}%`;

    // Probability Gauge
    const spamPercent = (data.spam_probability * 100).toFixed(1);
    const hamPercent = (data.ham_probability * 100).toFixed(1);
    spamPct.textContent = `${spamPercent}%`;
    hamPct.textContent = `${hamPercent}%`;
    probabilityMeter.value = Number(hamPercent);

    // Topic
    const tKey = data.topic || "unknown";
    topicIcon.textContent = TOPIC_ICONS[tKey] || "🎯";
    topicText.textContent = data.topic_display || tKey;
    topicConf.textContent = `${data.topic_confidence.toFixed(1)}%`;

    // Heuristics
    const h = data.heuristics || {};
    hCap.textContent = `${((h.cap_ratio || 0) * 100).toFixed(1)}%`;
    hCurrency.textContent = Math.round(h.currency_count || 0);
    hUrls.textContent = Math.round(h.url_count || 0);
    hExclamation.textContent = `${((h.exclamation_ratio || 0) * 100).toFixed(1)}%`;

    // Explainability
    renderWordPills(spamWordsList, data.explanation?.top_spam_words || [], "chip-spam", "+");
    renderWordPills(hamWordsList, data.explanation?.top_ham_words || [], "chip-ham", "");
  }

  function renderWordPills(container, words, chipClass, prefix) {
    container.innerHTML = "";
    if (!words || words.length === 0) {
      container.innerHTML = '<span class="no-words">None detected</span>';
      return;
    }

    words.forEach((item) => {
      const chip = document.createElement("span");
      chip.className = `word-chip ${chipClass}`;
      chip.innerHTML = `<strong>${escapeHtml(item.word)}</strong> <span>(${prefix}${item.impact.toFixed(2)})</span>`;
      container.appendChild(chip);
    });
  }

  function escapeHtml(str) {
    return str.replace(/[&<>'"]/g, (tag) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      "'": "&#39;",
      '"': "&quot;",
    }[tag] || tag));
  }

  // Fetch Benchmarks
  async function loadMetrics() {
    benchmarkTbody.innerHTML = '<tr><td colspan="7" class="loading-td">Loading test results...</td></tr>';
    try {
      const res = await fetch("/api/metrics");
      if (!res.ok) throw new Error("Metrics endpoint unavailable");
      const meta = await res.json();
      renderBenchmarkTable(meta);
    } catch (e) {
      console.warn("Could not load metrics:", e);
      benchmarkTbody.innerHTML = '<tr><td colspan="7" class="loading-td">Test results are unavailable.</td></tr>';
    }
  }

  function renderBenchmarkTable(meta) {
    const bench = meta.benchmark_results || {};
      const bestAlgo = meta.best_algorithm;
    benchmarkTbody.innerHTML = "";

    Object.keys(bench).forEach((algo) => {
      const s = bench[algo];
      const isBest = algo === bestAlgo;
      const tr = document.createElement("tr");
      if (isBest) tr.className = "highlighted-row";

      tr.innerHTML = `
        <td><strong>Email check</strong></td>
        <td>${(s.accuracy_mean * 100).toFixed(2)}%</td>
        <td>${(s.precision_mean * 100).toFixed(2)}%</td>
        <td>${(s.recall_mean * 100).toFixed(2)}%</td>
        <td>${(s.f1_mean * 100).toFixed(2)}%</td>
        <td>${(s.roc_auc_mean * 100).toFixed(2)}%</td>
        <td>${isBest ? '<span class="best-badge">Ready</span>' : '<span class="candidate-badge">Sample</span>'}</td>
      `;
      benchmarkTbody.appendChild(tr);
    });
  }

  refreshMetricsBtn.addEventListener("click", loadMetrics);

  // Initialize
  loadMetrics();
});

