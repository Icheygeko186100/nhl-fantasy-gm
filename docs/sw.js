const CACHE_NAME = 'nhl-fantasy-gm-v1.5.5';
const APP_SHELL = [
  './',
  './index.html',
  './manifest.json',
  './icon.svg',
  './apple-touch-icon.png',
  './icon-512.png',
  './css/styles.css',
  './js/engine.js',
  './js/multiplayer.js',
  './js/live-api.js',
  './js/app.js',
  './data/nhl-live-db.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET') return;

  // Live ESPN / NHL API requests: network-first
  if (url.hostname.includes('espn.com') || url.hostname.includes('nhle.com')) {
    event.respondWith(
      fetch(event.request).catch(() => caches.match(event.request))
    );
    return;
  }

  // App shell: stale-while-revalidate
  event.respondWith(
    caches.match(event.request).then((cached) => {
      const networkFetch = fetch(event.request).then((response) => {
        if (response && response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      }).catch(() => cached);
      return cached || networkFetch;
    })
  );
});
