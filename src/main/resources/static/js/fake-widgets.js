// Mouse-only widgets for the /inaccessible variant. Deliberately minimal:
// no keydown handling, no role/aria-expanded updates, no focus management.
document.addEventListener('click', function (event) {
    var fakeButton = event.target.closest('.fake-button, .fake-link');
    if (fakeButton) {
        if (fakeButton.dataset.href) {
            window.location.href = fakeButton.dataset.href;
        } else if (fakeButton.dataset.submit) {
            var form = fakeButton.closest('form');
            if (form) form.requestSubmit();
        }
        return;
    }

    var trigger = event.target.closest('.fake-select-trigger');
    if (trigger) {
        trigger.parentElement.classList.toggle('open');
        return;
    }

    var option = event.target.closest('.fake-select-option');
    if (option) {
        var widget = option.closest('.fake-select');
        widget.querySelector('.fake-select-trigger').textContent = option.textContent;
        widget.querySelector('input[type=hidden]').value = option.dataset.value;
        widget.classList.remove('open');
        return;
    }

    if (!event.target.closest('.fake-select')) {
        document.querySelectorAll('.fake-select.open').forEach(function (el) {
            el.classList.remove('open');
        });
    }
});
