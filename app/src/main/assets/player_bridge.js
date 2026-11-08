// Injected at document start into every frame. Finds the page's <video>, reports its state to the app
// and applies the commands it receives. Written for the old Chromium that an Android 9 box may run, and
// assumes nothing about the page: everything is guarded, and without a video it stays silent.
(function () {
  "use strict";

  var NONCE = "__NONCE__";
  var DIAGNOSTICS = __DIAGNOSTICS__;
  var STATE_INTERVAL_MS = 1000;
  var RESCAN_THROTTLE_MS = 500;
  var RESCAN_INTERVAL_MS = 2000;
  var RESCAN_PERIOD_MS = 60000;
  var ALLOWED_RATES = [0.75, 1, 1.25, 1.5];
  var EVENTS = ["play", "playing", "pause", "waiting", "seeked", "ended", "error", "loadedmetadata", "ratechange", "volumechange"];
  var FORWARDED_EVENTS = { playing: 1, pause: 1, waiting: 1, ended: 1, error: 1, loadedmetadata: 1 };

  var bridge = window.CodexBridge;
  if (!bridge || window.__codexBridgeInit) return;
  window.__codexBridgeInit = true;

  var main = null;
  var attached = [];
  var stateTimer = null;
  var styleInjected = false;
  var scanPending = false;
  var observer = null;

  function post(message) {
    try {
      message.n = NONCE;
      bridge.postMessage(JSON.stringify(message));
    } catch (e) {}
  }

  function finite(value) {
    return typeof value === "number" && isFinite(value) ? value : 0;
  }

  function bufferedEnd(video) {
    try {
      var ranges = video.buffered;
      var time = video.currentTime;
      for (var i = 0; i < ranges.length; i++) {
        if (ranges.start(i) <= time && time <= ranges.end(i)) return ranges.end(i);
      }
    } catch (e) {}
    return 0;
  }

  function postState() {
    if (!main) return;
    try {
      post({
        t: "state",
        ct: finite(main.currentTime),
        d: finite(main.duration),
        p: !!main.paused,
        b: !main.paused && !main.ended && main.readyState < 3,
        r: finite(main.playbackRate),
        v: finite(main.volume),
        m: !!main.muted,
        e: !!main.ended,
        bf: finite(bufferedEnd(main))
      });
    } catch (e) {}
  }

  function collectVideos(root, found) {
    var videos = root.querySelectorAll("video");
    for (var i = 0; i < videos.length; i++) found.push(videos[i]);
    var all = root.querySelectorAll("*");
    for (var j = 0; j < all.length; j++) {
      if (all[j].shadowRoot) collectVideos(all[j].shadowRoot, found);
    }
    return found;
  }

  function area(video) {
    try {
      var box = video.getBoundingClientRect();
      return box.width * box.height;
    } catch (e) {
      return 0;
    }
  }

  // Largest finite duration first, then largest rendered area.
  function isBetter(candidate, current) {
    var a = finite(candidate.duration);
    var b = finite(current.duration);
    return a !== b ? a > b : area(candidate) > area(current);
  }

  function injectStyle() {
    if (styleInjected) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var style = document.createElement("style");
    style.textContent = "video{object-fit:contain!important;background:#000!important}";
    parent.appendChild(style);
    styleInjected = true;
  }

  function onVideoEvent(event) {
    try {
      if (event.target !== main) {
        scheduleScan();
        return;
      }
      if (FORWARDED_EVENTS[event.type]) post({ t: "event", name: event.type });
      postState();
    } catch (e) {}
  }

  function attach(video) {
    if (attached.indexOf(video) !== -1) return;
    attached.push(video);
    EVENTS.forEach(function (name) {
      video.addEventListener(name, onVideoEvent);
    });
  }

  function scan() {
    try {
      var videos = collectVideos(document, []);
      var best = null;
      videos.forEach(function (video) {
        attach(video);
        if (!best || isBetter(video, best)) best = video;
      });
      // A full scan walks the whole page: once the film itself is found, stop watching every change.
      if (best && finite(best.duration) >= 60 && observer) {
        observer.disconnect();
        observer = null;
      }
      if (best === main) return;
      main = best;
      if (main) {
        injectStyle();
        postState();
        if (stateTimer === null) stateTimer = setInterval(postState, STATE_INTERVAL_MS);
      }
    } catch (e) {}
  }

  function scheduleScan() {
    if (scanPending) return;
    scanPending = true;
    setTimeout(function () {
      scanPending = false;
      scan();
    }, RESCAN_THROTTLE_MS);
  }

  function clampSeek(video, target) {
    var max = Math.max(0, finite(video.duration) - 1);
    return Math.min(Math.max(0, target), max);
  }

  function play(video) {
    var result = video.play();
    if (!result || !result.catch) return;
    result.catch(function (error) {
      if (!error || error.name !== "NotAllowedError") return;
      try {
        video.muted = true;
        post({ t: "event", name: "mutedAutoplay" });
        var retry = video.play();
        if (retry && retry.catch) retry.catch(function () {});
      } catch (e) {}
    });
  }

  function apply(command) {
    if (!main) return;
    var value = typeof command.value === "number" && isFinite(command.value) ? command.value : null;
    switch (command.cmd) {
      case "play":
        play(main);
        break;
      case "pause":
        main.pause();
        break;
      case "toggle":
        if (main.paused) play(main);
        else main.pause();
        break;
      case "seekTo":
        if (value !== null) main.currentTime = clampSeek(main, value);
        break;
      case "seekBy":
        if (value !== null) main.currentTime = clampSeek(main, finite(main.currentTime) + value);
        break;
      case "rate":
        if (ALLOWED_RATES.indexOf(value) !== -1) main.playbackRate = value;
        break;
      case "mute":
        main.muted = true;
        break;
      case "unmute":
        main.muted = false;
        break;
    }
    postState();
  }

  bridge.onmessage = function (event) {
    try {
      var command = JSON.parse(event.data);
      if (command && command.n === NONCE) apply(command);
    } catch (e) {}
  };

  try {
    observer = new MutationObserver(scheduleScan);
    observer.observe(document, { childList: true, subtree: true });
  } catch (e) {}
  document.addEventListener("DOMContentLoaded", scan);
  var rescans = setInterval(scan, RESCAN_INTERVAL_MS);
  setTimeout(function () {
    clearInterval(rescans);
  }, RESCAN_PERIOD_MS);

  // Debug builds only: one line every 15 s in logcat ("adb logcat | grep CodexDiag"), to see on a real box
  // whether the page plays HLS itself or through JavaScript, how many frames drop and what the stream answers.
  if (DIAGNOSTICS) {
    setInterval(function () {
      try {
        if (!main) return;
        var quality = main.getVideoPlaybackQuality ? main.getVideoPlaybackQuality() : {};
        var codes = {};
        (performance.getEntriesByType("resource") || []).forEach(function (entry) {
          if (!/\/hls2\//.test(entry.name)) return;
          var key = String(entry.responseStatus || "?") + (/[?&]gk=/.test(entry.name) ? "+gk" : "");
          codes[key] = (codes[key] || 0) + 1;
        });
        console.log("CodexDiag mode=" + (/^blob:/.test(main.currentSrc) ? "js" : "native") +
          " t=" + Math.round(finite(main.currentTime)) +
          " dropped=" + quality.droppedVideoFrames + "/" + quality.totalVideoFrames +
          " segments=" + JSON.stringify(codes));
      } catch (e) {}
    }, 15000);
  }

  post({ t: "ready" });
  scan();
})();

// Ad overlay guard, in every frame. The embed loads ad scripts from domains that change all the time, so a
// blocklist cannot keep up. On a TV they cannot open their tab, and fall back to a panel over the video, such
// as a QR code to "confirm you're not a robot". Only two things are hidden, never anything holding a <video>:
// an element whose text is that bait, and a foreign iframe laid over a large part of the screen, outside the
// player itself. Hiding is all it does: nothing is clicked or answered.
(function () {
  "use strict";

  var DIAGNOSTICS = __DIAGNOSTICS__;
  var BAIT = /not a robot|pas un robot|are you (a )?human|verify (that )?you('| a)re human|scan (the|this) (qr|code)|scannez (le|ce) (qr|code)/i;
  var MIN_COVER = 0.2;
  var TEXT_LIMIT = 2000;
  var SWEEP_DELAY_MS = 1000;

  if (window.__codexGuardInit) return;
  window.__codexGuardInit = true;

  var sweepPending = false;

  // The video provider's own hosts. Keep in step with MainFrameHostPolicy.PROVIDER_HOST.
  function isProviderHost(host) {
    return /(^|\.)vidzy\.(org|cc)$/i.test(host || "");
  }

  function hostOf(url) {
    try {
      return new URL(url, location.href).hostname;
    } catch (e) {
      return "";
    }
  }

  function hasVideo(el) {
    return el.tagName === "VIDEO" || !!(el.querySelector && el.querySelector("video"));
  }

  function isLaidOver(el) {
    var position = getComputedStyle(el).position;
    return position === "fixed" || position === "absolute";
  }

  function covers(el) {
    var rect = el.getBoundingClientRect();
    var screen = (window.innerWidth || 1) * (window.innerHeight || 1);
    return (rect.width * rect.height) / screen >= MIN_COVER;
  }

  // The outermost overlay around el that holds no video: the whole panel, not just its title.
  function overlayRoot(el) {
    var found = null;
    for (var node = el; node && node !== document.body && node !== document.documentElement; node = node.parentElement) {
      if (hasVideo(node)) break;
      if (isLaidOver(node)) found = node;
    }
    return found;
  }

  function hide(el, reason) {
    if (!el || el.getAttribute("data-codex-hidden")) return;
    el.style.setProperty("display", "none", "important");
    el.setAttribute("data-codex-hidden", reason);
    if (DIAGNOSTICS) console.log("CodexGuard hid " + reason + " " + el.tagName + " in " + location.hostname);
  }

  // The player's own box: ads it plays itself (a pre-roll, say) are left alone.
  function playerBox() {
    var video = document.querySelector("video");
    if (!video) return null;
    return (video.closest && video.closest(".video-js")) || video.parentElement;
  }

  function checkFrame(frame, box) {
    if (box && box.contains(frame)) return;
    var src = frame.getAttribute("src") || "";
    var foreign = /^https?:/i.test(src) ? !isProviderHost(hostOf(src)) : true;
    if (!foreign) return;
    var root = isLaidOver(frame) ? frame : overlayRoot(frame);
    if (root && !hasVideo(root) && covers(root)) hide(root, "frame");
  }

  function checkText(el) {
    var text = el.textContent || "";
    if (text.length > TEXT_LIMIT || !BAIT.test(text) || hasVideo(el)) return;
    if (window !== window.top && !isProviderHost(location.hostname)) {
      // The whole frame is the ad: blank it, its parent hides the frame itself.
      hide(document.documentElement, "bait");
      return;
    }
    hide(overlayRoot(el) || el, "bait");
  }

  function sweep() {
    sweepPending = false;
    try {
      var body = document.body;
      if (!body) return;
      var box = playerBox();
      var frames = body.getElementsByTagName("iframe");
      for (var i = 0; i < frames.length; i++) checkFrame(frames[i], box);
      var candidates = body.querySelectorAll("div,section,aside,dialog,a,span,p,h1,h2,h3");
      for (var j = 0; j < candidates.length; j++) {
        var el = candidates[j];
        // Only the innermost elements carrying the text, so a short check per element.
        if (el.children.length <= 3) checkText(el);
      }
    } catch (e) {}
  }

  function scheduleSweep() {
    if (sweepPending) return;
    sweepPending = true;
    setTimeout(sweep, SWEEP_DELAY_MS);
  }

  try {
    // Nodes being added is enough: the player's clock rewrites text every second, and is no panel.
    new MutationObserver(scheduleSweep).observe(document, { childList: true, subtree: true });
  } catch (e) {}
  document.addEventListener("DOMContentLoaded", scheduleSweep);
})();
