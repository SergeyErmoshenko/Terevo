import type { NodeDto } from "../../app/dto";
import type { Point } from "./camera";

// Linear scan is fine at this scale (hundreds of cards, not tens of thousands) and keeps this
// trivially testable, unlike the old Kotlin SpatialIndex's grid buckets which existed mainly to
// cull what the renderer iterates - Canvas2D redraws every node every frame regardless, so the
// cull would only have saved hit-testing time, not paint time.
export function hitTestNode(nodes: NodeDto[], point: Point): NodeDto | undefined {
  // Later entries were added later; prefer them on overlap, same as iterating a Map in old code.
  for (let index = nodes.length - 1; index >= 0; index -= 1) {
    const node = nodes[index];
    if (point.x >= node.x && point.x <= node.x + node.width && point.y >= node.y && point.y <= node.y + node.height) {
      return node;
    }
  }
  return undefined;
}
