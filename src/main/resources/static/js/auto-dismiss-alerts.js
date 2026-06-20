/*
 * Created on 2026-06-21: Shared auto-dismiss behavior for flash notifications.
 * Created by: NinhDD - HE186113
 */
(function () {
    const DEFAULT_DELAY = 5000;

    function dismiss(el) {
        if (!el || el.dataset.dismissed === 'true') {
            return;
        }
        el.dataset.dismissed = 'true';

        if (window.bootstrap && window.bootstrap.Alert && el.classList.contains('alert')) {
            try {
                window.bootstrap.Alert.getOrCreateInstance(el).close();
                return;
            } catch (ignored) {
                // Fall back to a small manual transition below.
            }
        }

        el.style.transition = 'opacity 0.35s ease, transform 0.35s ease, max-height 0.35s ease, margin 0.35s ease, padding 0.35s ease';
        el.style.opacity = '0';
        el.style.transform = 'translateY(-6px)';
        el.style.maxHeight = el.scrollHeight + 'px';

        window.setTimeout(function () {
            el.style.maxHeight = '0';
            el.style.marginTop = '0';
            el.style.marginBottom = '0';
            el.style.paddingTop = '0';
            el.style.paddingBottom = '0';
        }, 30);

        window.setTimeout(function () {
            el.remove();
        }, 420);
    }

    function bindAutoDismiss() {
        document.querySelectorAll('[data-auto-dismiss]').forEach(function (el) {
            if (el.dataset.autoDismissBound === 'true') {
                return;
            }
            el.dataset.autoDismissBound = 'true';
            const delay = Number.parseInt(el.dataset.autoDismiss, 10) || DEFAULT_DELAY;
            window.setTimeout(function () {
                dismiss(el);
            }, delay);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bindAutoDismiss);
    } else {
        bindAutoDismiss();
    }
})();
