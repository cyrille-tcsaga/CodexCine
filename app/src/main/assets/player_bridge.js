// Injected at document start into every frame. Finds the page's <video>, reports its state to the app
// and applies the commands it receives. Written for the old Chromium that an Android 9 box may run, and
// assumes nothing about the page: everything is guarded, and without a video it stays silent.
(function () {
  "use strict";

  var NONCE = "__NONCE__";
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
    new MutationObserver(scheduleScan).observe(document, { childList: true, subtree: true });
  } catch (e) {}
  document.addEventListener("DOMContentLoaded", scan);
  var rescans = setInterval(scan, RESCAN_INTERVAL_MS);
  setTimeout(function () {
    clearInterval(rescans);
  }, RESCAN_PERIOD_MS);

  post({ t: "ready" });
  scan();
})();
