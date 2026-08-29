#!/usr/bin/env node
// joke-cli.js - Node.js CLI example (requires Node 18+ for native fetch)
(async () => {
  try {
    const res = await fetch('https://icanhazdadjoke.com/', {
      headers: { Accept: 'application/json', 'User-Agent': 'joke-cli' }
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    console.log('\n', data.joke, '\n');
  } catch (err) {
    console.error('Failed to fetch joke:', err.message);
  }
})();
