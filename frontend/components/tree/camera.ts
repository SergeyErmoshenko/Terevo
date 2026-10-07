// Mirrors me/terevo/ui/tree/Camera.kt exactly: a pure view transform, screen = world * scale + offset.

export interface Point {
  x: number;
  y: number;
}

export interface Size {
  width: number;
  height: number;
}

export interface Rect {
  left: number;
  top: number;
  width: number;
  height: number;
}

export const MIN_SCALE = 0.1;
export const MAX_SCALE = 4.0;
export const DEFAULT_SCALE = 1.0;

export interface Camera {
  scale: number;
  offset: Point;
}

export function defaultCamera(): Camera {
  return { scale: DEFAULT_SCALE, offset: { x: 0, y: 0 } };
}

export function worldToScreen(camera: Camera, point: Point): Point {
  return { x: point.x * camera.scale + camera.offset.x, y: point.y * camera.scale + camera.offset.y };
}

export function screenToWorld(camera: Camera, point: Point): Point {
  return { x: (point.x - camera.offset.x) / camera.scale, y: (point.y - camera.offset.y) / camera.scale };
}

export function pan(camera: Camera, delta: Point): Camera {
  return { scale: camera.scale, offset: { x: camera.offset.x + delta.x, y: camera.offset.y + delta.y } };
}

export function zoomAt(camera: Camera, screenPoint: Point, factor: number): Camera {
  const world = screenToWorld(camera, screenPoint);
  const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, camera.scale * factor));
  return {
    scale: newScale,
    offset: { x: screenPoint.x - world.x * newScale, y: screenPoint.y - world.y * newScale },
  };
}

export function fit(bounds: Rect, viewport: Size, padding: number): Camera {
  if (bounds.width <= 0 || bounds.height <= 0) return defaultCamera();
  const availableWidth = Math.max(1, viewport.width - padding * 2);
  const availableHeight = Math.max(1, viewport.height - padding * 2);
  const fittedScale = Math.min(
    MAX_SCALE,
    Math.max(MIN_SCALE, Math.min(availableWidth / bounds.width, availableHeight / bounds.height)),
  );
  const clamped = Math.min(fittedScale, DEFAULT_SCALE);
  return {
    scale: clamped,
    offset: {
      x: (viewport.width - bounds.width * clamped) / 2 - bounds.left * clamped,
      y: (viewport.height - bounds.height * clamped) / 2 - bounds.top * clamped,
    },
  };
}

export function center(camera: Camera, rect: Rect, viewport: Size): Camera {
  const centerX = rect.left + rect.width / 2;
  const centerY = rect.top + rect.height / 2;
  return {
    scale: camera.scale,
    offset: { x: viewport.width / 2 - centerX * camera.scale, y: viewport.height / 2 - centerY * camera.scale },
  };
}

export function actualSize(camera: Camera): Camera {
  return { scale: 1, offset: camera.offset };
}

// Fixed step per scroll event, deliberately small: only the scroll direction is read, not its
// magnitude, so a hard flick and a gentle one zoom at the same speed.
export const ZOOM_STEP = 1.012;

export function visibleWorld(camera: Camera, viewportWidth: number, viewportHeight: number, marginScreens = 1): Rect {
  const topLeft = screenToWorld(camera, { x: -viewportWidth * marginScreens, y: -viewportHeight * marginScreens });
  const bottomRight = screenToWorld(camera, {
    x: viewportWidth * (1 + marginScreens),
    y: viewportHeight * (1 + marginScreens),
  });
  return { left: topLeft.x, top: topLeft.y, width: bottomRight.x - topLeft.x, height: bottomRight.y - topLeft.y };
}
