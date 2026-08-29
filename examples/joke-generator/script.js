// script.js - fetches jokes from icanhazdadjoke.com
const jokeText = document.getElementById('joke-text');
const newBtn = document.getElementById('new-joke');
const copyBtn = document.getElementById('copy-joke');
const shareBtn = document.getElementById('share-joke');

async function fetchJoke() {
  setLoading(true);
  try {
    const res = await fetch('https://icanhazdadjoke.com/', {
      headers: { Accept: 'application/json', 'User-Agent': 'Random-Joke-Generator (example)' }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    jokeText.textContent = data?.joke ?? 'No joke found.';
  } catch (err) {
    console.error(err);
    jokeText.textContent = 'Failed to fetch a joke. Try again.';
  } finally {
    setLoading(false);
  }
}

function setLoading(isLoading) {
  if (isLoading) {
    jokeText.textContent = 'Loading…';
    newBtn.disabled = true;
  } else {
    newBtn.disabled = false;
  }
}

newBtn.addEventListener('click', fetchJoke);

copyBtn.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(jokeText.textContent);
    copyBtn.textContent = 'Copied!';
    setTimeout(() => (copyBtn.textContent = 'Copy'), 1200);
  } catch {
    copyBtn.textContent = 'Failed';
    setTimeout(() => (copyBtn.textContent = 'Copy'), 1200);
  }
});

shareBtn.addEventListener('click', async () => {
  const text = jokeText.textContent;
  if (navigator.share) {
    try {
      await navigator.share({ title: 'Joke', text });
    } catch (err) {
      console.warn('Share cancelled or failed', err);
    }
  } else {
    // fallback: copy to clipboard
    try {
      await navigator.clipboard.writeText(text);
      shareBtn.textContent = 'Copied!';
      setTimeout(() => (shareBtn.textContent = 'Share'), 1200);
    } catch {
      shareBtn.textContent = 'Failed';
      setTimeout(() => (shareBtn.textContent = 'Share'), 1200);
    }
  }
});

// fetch a joke on load
fetchJoke();
