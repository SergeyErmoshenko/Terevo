import { test } from "node:test";
import assert from "node:assert/strict";
import { hitTestNode } from "../frontend/components/tree/hitTest.ts";

const nodes = [
  { id: "a", x: 0, y: 0, width: 100, height: 50 },
  { id: "b", x: 200, y: 0, width: 100, height: 50 },
];

test("hitTestNode finds the node containing the point", () => {
  assert.equal(hitTestNode(nodes, { x: 50, y: 25 })?.id, "a");
  assert.equal(hitTestNode(nodes, { x: 250, y: 25 })?.id, "b");
});

test("hitTestNode returns undefined outside every node", () => {
  assert.equal(hitTestNode(nodes, { x: 150, y: 25 }), undefined);
});

test("hitTestNode prefers the later node on overlap (last added wins)", () => {
  const overlapping = [
    { id: "first", x: 0, y: 0, width: 100, height: 100 },
    { id: "second", x: 50, y: 50, width: 100, height: 100 },
  ];
  assert.equal(hitTestNode(overlapping, { x: 75, y: 75 })?.id, "second");
});

test("hitTestNode treats edges as inclusive", () => {
  assert.equal(hitTestNode(nodes, { x: 0, y: 0 })?.id, "a");
  assert.equal(hitTestNode(nodes, { x: 100, y: 50 })?.id, "a");
});
