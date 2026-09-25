const toastRootSelector = '.Toastify';
const modalStack: HTMLDialogElement[] = [];

function activeModal(): HTMLDialogElement | null {
  for (let index = modalStack.length - 1; index >= 0; index -= 1) {
    const modal = modalStack[index];
    if (modal?.isConnected && modal.open) return modal;
    modalStack.splice(index, 1);
  }
  return null;
}

function promoteToTopLayer(toastRoot: HTMLElement): boolean {
  if (typeof toastRoot.showPopover !== 'function') return false;

  toastRoot.setAttribute('popover', 'manual');
  try {
    toastRoot.hidePopover();
  } catch {
    // The root was not open yet.
  }
  if (toastRoot.parentElement !== document.body) document.body.appendChild(toastRoot);

  try {
    toastRoot.showPopover();
    return true;
  } catch {
    return false;
  }
}

/**
 * Native modal dialogs live in the browser top layer. A toast mounted directly
 * below document.body cannot render above that layer, regardless of z-index.
 * Prefer a non-modal popover in that same top layer. Reparenting into the active
 * dialog remains as a fallback for browsers without the Popover API.
 */
export function syncToastLayer(): void {
  if (typeof document === 'undefined') return;
  const toastRoot = document.querySelector<HTMLElement>(toastRootSelector);
  if (!toastRoot) return;
  if (promoteToTopLayer(toastRoot)) return;

  const target = activeModal() ?? document.body;
  if (toastRoot.parentElement !== target) target.appendChild(toastRoot);
}

export function registerToastModal(modal: HTMLDialogElement): () => void {
  const existingIndex = modalStack.indexOf(modal);
  if (existingIndex >= 0) modalStack.splice(existingIndex, 1);
  modalStack.push(modal);
  syncToastLayer();

  let registered = true;
  return () => {
    if (!registered) return;
    registered = false;
    const index = modalStack.lastIndexOf(modal);
    if (index >= 0) modalStack.splice(index, 1);
    syncToastLayer();
  };
}
