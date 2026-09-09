// Progressive enhancement for the /accessible registration form: intercepts submit and swaps
// in the response's <body>, so a validation error becomes a genuine DOM-mutation live-region
// update -- the thing NVDA actually announces proactively, instead of a full page reload.
// Without this script (or with JS disabled), the form still works as a normal synchronous POST.
//
// Listens on `document` (event delegation) rather than the form directly: the swap replaces
// the whole body, including the form itself, so a listener bound to the original form node
// would go dead after the first submit.
document.addEventListener('submit', async (event) => {
    const form = event.target.closest('form[data-async]');
    if (!form) return;
    event.preventDefault();

    const response = await fetch(form.action, {
        method: 'POST',
        body: new URLSearchParams(new FormData(form)),
        headers: {'Content-Type': 'application/x-www-form-urlencoded'}
    });
    const html = await response.text();
    const parsed = new DOMParser().parseFromString(html, 'text/html');

    document.title = parsed.title;
    document.body.innerHTML = parsed.body.innerHTML;

    const target = document.querySelector('[role="alert"], h1');
    if (target) {
        target.setAttribute('tabindex', '-1');
        target.focus();
    }
});
