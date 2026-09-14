import { useCallback, useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';

/** Measure the actual overlays, including mobile sheets, before moving the camera. */
export function useMapCamera(rootRef: RefObject<HTMLElement | null>, mapRef: RefObject<L.Map | null>) {
  const focusRef = useRef<L.LatLng | null>(null);
  const frameRef = useRef(0);
  const getViewport = useCallback(() => {
    const root = rootRef.current;
    const rect = root?.getBoundingClientRect();
    const width = rect?.width ?? 1;
    const height = rect?.height ?? 1;
    const padding = { left: 24, right: 24, top: 24, bottom: 80 };
    const sheet = window.matchMedia('(max-width: 899px), (max-height: 650px)').matches;
    root?.querySelectorAll<HTMLElement>('[data-map-edge]').forEach((element) => {
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
    // Reserve the mobile toolbar plus the full marker radius, not just its anchor.
    if (sheet) padding.top = Math.max(padding.top, 150);
    const center = L.point((padding.left + width - padding.right) / 2, (padding.top + height - padding.bottom) / 2);
    root?.style.setProperty('--safe-center-x', `${center.x}px`);
    return { ...padding, width, height, center };
  }, [rootRef]);

  const focusLocation = useCallback((position: L.LatLngExpression, zoom?: number) => {
    focusRef.current = L.latLng(position);
    cancelAnimationFrame(frameRef.current);
    frameRef.current = requestAnimationFrame(() => {
      const map = mapRef.current;
      if (!map) return;
      const view = getViewport();
      const targetZoom = zoom ?? map.getZoom();
      const offset = view.center.subtract(L.point(view.width / 2, view.height / 2));
      const center = map.unproject(map.project(position, targetZoom).subtract(offset), targetZoom);
      map.setView(center, targetZoom, { animate: false });
    });
  }, [getViewport, mapRef]);

  const fitBounds = useCallback((bounds: L.LatLngBounds) => {
    focusRef.current = null;
    cancelAnimationFrame(frameRef.current);
    frameRef.current = requestAnimationFrame(() => {
      const map = mapRef.current;
      if (!map || !bounds.isValid()) return;
      map.invalidateSize({ pan: false });
      const view = getViewport();
      map.fitBounds(bounds, {
        paddingTopLeft: [view.left, view.top],
        paddingBottomRight: [view.right, view.bottom],
        maxZoom: 16, animate: false,
      });
    });
  }, [getViewport, mapRef]);

  const getVisibleCenter = useCallback(() => mapRef.current?.containerPointToLatLng(getViewport().center), [getViewport, mapRef]);
  const releaseFocus = useCallback(() => { focusRef.current = null; }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    let resizeFrame = 0;
    const resize = () => {
      cancelAnimationFrame(resizeFrame);
      resizeFrame = requestAnimationFrame(() => {
        const map = mapRef.current;
        if (!map) return;
        map.invalidateSize({ pan: false });
        const view = getViewport();
        if (focusRef.current) {
          const point = map.latLngToContainerPoint(focusRef.current);
          if (point.x < view.left || point.x > view.width - view.right || point.y < view.top || point.y > view.height - view.bottom) {
            focusLocation(focusRef.current);
          }
        }
      });
    };
    const observer = new ResizeObserver(resize);
    observer.observe(root);
    root.querySelectorAll('[data-map-edge]').forEach((node) => observer.observe(node));
    resize();
    return () => { observer.disconnect(); cancelAnimationFrame(resizeFrame); cancelAnimationFrame(frameRef.current); };
  }, [rootRef, mapRef, getViewport, focusLocation]);

  return { focusLocation, fitBounds, getVisibleCenter, releaseFocus };
}
