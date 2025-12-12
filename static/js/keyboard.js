// Virtual Keyboard Module
// Shared keyboard functionality for all pages

(function() {
    let activeInput = null;
    let shiftState = 0; // 0 = off, 1 = single shift, 2 = caps lock
    let onSubmitCallback = null;

    // Expose functions globally
    window.activateKeyboard = function(input, onSubmit) {
        activeInput = input;
        onSubmitCallback = onSubmit || null;
        document.getElementById('virtualKeyboard').classList.add('active');
        document.body.classList.add('keyboard-open');
    };

    window.hideKeyboard = function() {
        document.getElementById('virtualKeyboard').classList.remove('active');
        document.body.classList.remove('keyboard-open');
        if (activeInput) {
            activeInput.blur();
        }
        activeInput = null;
        onSubmitCallback = null;
    };

    // Initialize keyboard when DOM is ready
    function initKeyboard() {
        const keyboard = document.getElementById('virtualKeyboard');
        if (!keyboard) return;

        keyboard.querySelectorAll('.key').forEach(key => {
            key.addEventListener('click', handleKeyPress);
        });

        // Close keyboard when tapping outside
        document.addEventListener('click', function(e) {
            if (!keyboard.classList.contains('active')) return;

            // Check if click is inside keyboard or on an input that uses keyboard
            const isInsideKeyboard = keyboard.contains(e.target);
            const isKeyboardInput = e.target.hasAttribute('onfocus') &&
                                    e.target.getAttribute('onfocus').includes('activateKeyboard');

            if (!isInsideKeyboard && !isKeyboardInput) {
                window.hideKeyboard();
            }
        });
    }

    function handleKeyPress(e) {
        e.preventDefault();
        if (!activeInput) return;

        const key = e.currentTarget;
        const action = key.dataset.action;

        if (action === 'shift') {
            shiftState = (shiftState + 1) % 3;
            updateShiftDisplay();
            return;
        }

        if (action === 'backspace') {
            const start = activeInput.selectionStart;
            const end = activeInput.selectionEnd;
            if (start !== end) {
                activeInput.value = activeInput.value.slice(0, start) + activeInput.value.slice(end);
                activeInput.setSelectionRange(start, start);
            } else if (start > 0) {
                activeInput.value = activeInput.value.slice(0, start - 1) + activeInput.value.slice(start);
                activeInput.setSelectionRange(start - 1, start - 1);
            }
            activeInput.dispatchEvent(new Event('input', { bubbles: true }));
            return;
        }

        if (action === 'submit') {
            if (onSubmitCallback) {
                onSubmitCallback();
            } else {
                // Trigger form submit or enter key
                activeInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            }
            hideKeyboard();
            return;
        }

        // Regular character input
        let char = key.dataset.key;
        if (!char) return;

        if (shiftState > 0 && key.dataset.shift) {
            char = key.dataset.shift;
        } else if (shiftState > 0 && key.classList.contains('key-letter')) {
            char = char.toUpperCase();
        }

        const start = activeInput.selectionStart;
        const end = activeInput.selectionEnd;
        activeInput.value = activeInput.value.slice(0, start) + char + activeInput.value.slice(end);
        activeInput.setSelectionRange(start + 1, start + 1);
        activeInput.dispatchEvent(new Event('input', { bubbles: true }));

        // Turn off single shift after typing
        if (shiftState === 1) {
            shiftState = 0;
            updateShiftDisplay();
        }
    }

    function updateShiftDisplay() {
        const keyboard = document.getElementById('virtualKeyboard');

        // Update keyboard classes
        keyboard.classList.toggle('shifted', shiftState > 0);
        keyboard.classList.toggle('caps-locked', shiftState === 2);
        keyboard.classList.toggle('shift-active', shiftState > 0);

        // Update all shift keys appearance
        keyboard.querySelectorAll('.key-shift').forEach(shiftKey => {
            shiftKey.classList.remove('shift-single', 'shift-caps');
            if (shiftState === 1) {
                shiftKey.classList.add('shift-single');
            } else if (shiftState === 2) {
                shiftKey.classList.add('shift-caps');
            }
        });

        // Update letter keys to show uppercase/lowercase
        keyboard.querySelectorAll('.key-letter').forEach(key => {
            const baseChar = key.dataset.key;
            key.textContent = shiftState > 0 ? baseChar.toUpperCase() : baseChar.toLowerCase();
        });
    }

    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initKeyboard);
    } else {
        initKeyboard();
    }
})();
