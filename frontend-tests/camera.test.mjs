import { test } from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_SCALE,
  MAX_SCALE,
  MIN_SCALE,
  actualSize,
  center,
  defaultCamera,
  fit,
  pan,
  screenToWorld,
  worldToScreen,
  zoomAt,
} from "../frontend/components/tree/camera.ts";

test("world/screen transforms round-trip", () => {
  const camera = { scale: 2, offset: { x: 10, y: -5 } };
  const world = { x: 37, y: 12 };
  const screen = worldToScreen(camera, world);
  const back = screenToWorld(camera, screen);
  assert.ok(Math.abs(back.x - world.x) < 1e-9);
  assert.ok(Math.abs(back.y - world.y) < 1e-9);
});

test("pan only moves the offset", () => {
  const camera = { scale: 1.5, offset: { x: 1, y: 2 } };
  const panned = pan(camera, { x: 10, y: -4 });
  assert.equal(panned.scale, 1.5);
  assert.deepEqual(panned.offset, { x: 11, y: -2 });
});

test("zoomAt keeps the screen point fixed in world space", () => {
  const camera = { scale: 1, offset: { x: 0, y: 0 } };
  const screenPoint = { x: 100, y: 50 };
  const before = screenToWorld(camera, screenPoint);
  const zoomed = zoomAt(camera, screenPoint, 2);
  const after = screenToWorld(zoomed, screenPoint);
  assert.ok(Math.abs(before.x - after.x) < 1e-9);
  assert.ok(Math.abs(before.y - after.y) < 1e-9);
  assert.equal(zoomed.scale, 2);
});

test("zoomAt clamps to the min/max scale", () => {
  const camera = defaultCamera();
  assert.equal(zoomAt(camera, { x: 0, y: 0 }, 0.0001).scale, MIN_SCALE);
  assert.equal(zoomAt(camera, { x: 0, y: 0 }, 10000).scale, MAX_SCALE);
});

test("fit centers the bounds and never zooms in past 100%", () => {
  const bounds = { left: 0, top: 0, width: 200, height: 100 };
  const camera = fit(bounds, { width: 1000, height: 1000 }, 0);
  assert.ok(camera.scale <= DEFAULT_SCALE);
  const topLeft = worldToScreen(camera, { x: 0, y: 0 });
  const bottomRight = worldToScreen(camera, { x: 200, y: 100 });
  const centerX = (topLeft.x + bottomRight.x) / 2;
  const centerY = (topLeft.y + bottomRight.y) / 2;
  assert.ok(Math.abs(centerX - 500) < 1e-6);
  assert.ok(Math.abs(centerY - 500) < 1e-6);
});

test("fit on a bounds wider than the viewport zooms out to fit, not past MIN_SCALE floor logic", () => {
  const bounds = { left: 0, top: 0, width: 10000, height: 10000 };
  const camera = fit(bounds, { width: 800, height: 600 }, 20);
  assert.ok(camera.scale < DEFAULT_SCALE);
  assert.ok(camera.scale >= MIN_SCALE);
});

test("center puts the rect's center at the middle of the viewport", () => {
  const camera = { scale: 2, offset: { x: 0, y: 0 } };
  const rect = { left: 10, top: 10, width: 20, height: 20 };
  const centered = center(camera, rect, { width: 400, height: 300 });
  const screenCenter = worldToScreen(centered, { x: 20, y: 20 });
  assert.ok(Math.abs(screenCenter.x - 200) < 1e-9);
  assert.ok(Math.abs(screenCenter.y - 150) < 1e-9);
});

test("actualSize resets scale to 1 without moving the offset", () => {
  const camera = { scale: 3, offset: { x: 5, y: 6 } };
  const reset = actualSize(camera);
  assert.equal(reset.scale, 1);
  assert.deepEqual(reset.offset, { x: 5, y: 6 });
});
