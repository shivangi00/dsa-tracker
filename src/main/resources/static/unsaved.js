// Unsaved edits: each editor registers how to tell whether it has changes. Before leaving or
// reloading the page with unsaved changes, the browser asks "Leave site?", so edits aren't lost.
const editors = new Set();

/** @param node the editor's element (forgotten once it leaves the page) @param changed () => boolean */
export function trackUnsaved(node, changed) {
  editors.add({ node, changed });
}

export function hasUnsaved() {
  for (const e of [...editors]) {
    if (!e.node.isConnected) { editors.delete(e); continue; }
    if (e.changed()) return true;
  }
  return false;
}

window.addEventListener('beforeunload', (ev) => {
  if (hasUnsaved()) {
    ev.preventDefault();
    ev.returnValue = '';
  }
});
