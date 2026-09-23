import { onScopeDispose, watch, type ShallowRef } from 'vue';
import L from 'leaflet';

/** Measure the actual overlays (including mobile sheets) before moving the camera. */
export function useMapCamera(rootRef: ShallowRef<HTMLElement | null>, mapRef: ShallowRef<L.Map | null>) {
  let focus: L.LatLng | null = null, frame = 0;
  const getViewport = () => {
    const root = rootRef.value, rect = root?.getBoundingClientRect();
    const width = rect?.width ?? 1, height = rect?.height ?? 1;
    const padding = { left: 24, right: 24, top: 24, bottom: 80 };
    const sheet = window.matchMedia('(max-width: 899px), (max-height: 650px)').matches;
    root?.querySelectorAll<HTMLElement>('[data-map-edge]').forEach(element => {
      if (!element.checkVisibility() || !rect) return;
      const box = element.getBoundingClientRect();
      if (!box.width || !box.height) return;
      let edge = element.dataset.mapEdge;
      if (sheet && (edge === 'left' || edge === 'right')) edge = height <= 480 ? 'left' : 'bottom';
      if (edge === 'left') padding.left = Math.max(padding.left, box.right - rect.left + 24);
      if (edge === 'right') padding.right = Math.max(padding.right, rect.right - box.left + 24);
      if (edge === 'top') padding.top = Math.max(padding.top, box.bottom - rect.top + 24);
      if (edge === 'bottom') padding.bottom = Math.max(padding.bottom, rect.bottom - box.top + 24);
    });
    if (sheet) padding.top = Math.max(padding.top, 150);
    const center = L.point((padding.left + width - padding.right) / 2, (padding.top + height - padding.bottom) / 2);
    root?.style.setProperty('--safe-center-x', `${center.x}px`); root?.style.setProperty('--safe-center-y', `${center.y}px`);
    return { ...padding, width, height, center };
  };
  const focusLocation = (position: L.LatLngExpression, zoom?: number) => {
    focus = L.latLng(position); cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const map = mapRef.value;
      if (!map) return;
      const view = getViewport(), targetZoom = zoom ?? map.getZoom();
      const offset = view.center.subtract(L.point(view.width / 2, view.height / 2));
      const center = map.unproject(map.project(position, targetZoom).subtract(offset), targetZoom);
      map.setView(center, targetZoom, { animate: false });
    });
  };
  const fitBounds = (bounds: L.LatLngBounds) => {
    focus = null; cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const map = mapRef.value;
      if (!map || !bounds.isValid()) return;
      map.invalidateSize({ pan: false });
      const view = getViewport();
      map.fitBounds(bounds, { paddingTopLeft: [view.left, view.top], paddingBottomRight: [view.right, view.bottom], maxZoom: 16, animate: false });
    });
  };
  watch(rootRef, (root, _previous, cleanup) => {
    if (!root) return;
    let resizeFrame = 0;
    const resize = () => {
      cancelAnimationFrame(resizeFrame);
      resizeFrame = requestAnimationFrame(() => {
        const map = mapRef.value;
        if (!map) return;
        map.invalidateSize({ pan: false });
        const view = getViewport();
        if (focus) {
          const point = map.latLngToContainerPoint(focus);
          if (point.x < view.left || point.x > view.width - view.right || point.y < view.top || point.y > view.height - view.bottom) focusLocation(focus);
        }
      });
    };
    const observer = new ResizeObserver(resize);
    observer.observe(root); root.querySelectorAll('[data-map-edge]').forEach(node => observer.observe(node)); resize();
    cleanup(() => { observer.disconnect(); cancelAnimationFrame(resizeFrame); cancelAnimationFrame(frame); });
  }, { immediate: true, flush: 'post' });
  onScopeDispose(() => cancelAnimationFrame(frame));
  return { focusLocation, fitBounds, getVisibleCenter: () => mapRef.value?.containerPointToLatLng(getViewport().center), releaseFocus: () => { focus = null; } };
}
