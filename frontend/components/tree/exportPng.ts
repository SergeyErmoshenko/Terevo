import type {AppStateDto} from "../../app/dto";
import {fileUrl, type TerevoBridge} from "../../app/client";
import type {Camera} from "./camera";
import {drawEdge, drawGenerationBands, drawNode, readThemeColors} from "./TreeCanvas";

const PADDING = 48;
// The longest side of the picture. A single <canvas> cannot be this large (Chromium stops at
// 16384 px), so the picture is painted in small tiles that are streamed to the main process, which
// encodes the PNG row by row without ever holding the whole image in memory.
const MAX_SIDE = 40_000;
const MAX_PIXELS = 1_000_000_000;
const MAX_SCALE = 4;
const TILE_WIDTH = 4096;
const TILE_HEIGHT = 128;

// Photos are loaded again here with crossOrigin set: the on-screen images are not CORS-clean, and
// drawing even one of those would taint the canvas and make getImageData() throw.
function loadImage(url: string): Promise<HTMLImageElement | null> {
    return new Promise((resolve) => {
        const image = new Image();
        image.crossOrigin = "anonymous";
        image.onload = () => resolve(image);
        image.onerror = () => resolve(null);
        image.src = url;
    });
}

// Renders the whole tree (not just the visible part) in the light theme, so the picture is
// readable when printed or shared regardless of the theme the app is showing, and writes it to
// `path`. Returns whether the file was written.
export async function exportTreePng(state: AppStateDto, bridge: TerevoBridge, path: string): Promise<boolean> {
    const layout = state.canvas.layout;
    if (!layout || layout.nodes.length === 0) return false;

    const probe = document.createElement("div");
    probe.className = "theme-light";
    probe.style.display = "none";
    document.body.appendChild(probe);
    const colors = readThemeColors(probe);
    document.body.removeChild(probe);

    const images = new Map<string, HTMLImageElement>();
    await Promise.all(
        Object.values(layout.visuals).map(async (visual) => {
            const url = fileUrl(visual.thumbnailPath);
            if (!url || images.has(url)) return;
            const image = await loadImage(url);
            if (image) images.set(url, image);
        }),
    );

    const worldWidth = Math.max(layout.maxX - layout.minX, 1);
    const worldHeight = Math.max(layout.maxY - layout.minY, 1);
    // Small trees get up to MAX_SCALE times magnification; big ones the largest scale that keeps the
    // longest side within MAX_SIDE.
    const scale = Math.min(
        MAX_SCALE,
        (MAX_SIDE - 2 * PADDING) / worldWidth,
        (MAX_SIDE - 2 * PADDING) / worldHeight,
        Math.sqrt(MAX_PIXELS / ((worldWidth + 2 * PADDING) * (worldHeight + 2 * PADDING))),
    );
    const width = Math.ceil(worldWidth * scale + 2 * PADDING);
    const height = Math.ceil(worldHeight * scale + 2 * PADDING);

    const tile = document.createElement("canvas");
    tile.width = TILE_WIDTH;
    tile.height = TILE_HEIGHT;
    const ctx = tile.getContext("2d", {willReadFrequently: true});
    if (!ctx) return false;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";

    if (!(await bridge.pngBegin(path, width, height))) return false;
    try {
        const visualOf = (id: string) => layout.visuals[id];
        for (let top = 0; top < height; top += TILE_HEIGHT) {
            const rows = Math.min(TILE_HEIGHT, height - top);
            const strip = new Uint8Array(width * rows * 3);
            for (let left = 0; left < width; left += TILE_WIDTH) {
                const columns = Math.min(TILE_WIDTH, width - left);
                const camera: Camera = {
                    scale,
                    offset: {x: PADDING - layout.minX * scale - left, y: PADDING - layout.minY * scale - top},
                };
                // The tile in world coordinates, so only what overlaps it is drawn.
                const viewport = {
                    left: (0 - camera.offset.x) / scale,
                    top: (0 - camera.offset.y) / scale,
                    right: (columns - camera.offset.x) / scale,
                    bottom: (rows - camera.offset.y) / scale,
                };

                ctx.save();
                ctx.beginPath();
                ctx.rect(0, 0, columns, rows);
                ctx.clip();
                ctx.fillStyle = colors.canvasBg;
                ctx.fillRect(0, 0, columns, rows);
                drawGenerationBands(ctx, camera, layout, colors, viewport, state.layoutDirection === "LEFT_RIGHT");
                for (const edge of layout.edges) drawEdge(ctx, camera, edge, "NEUTRAL", colors);
                for (const node of layout.nodes) {
                    const overlaps =
                        node.x < viewport.right + 40 &&
                        node.x + node.width > viewport.left - 40 &&
                        node.y < viewport.bottom + 40 &&
                        node.y + node.height > viewport.top - 40;
                    if (overlaps) drawNode(ctx, camera, node, visualOf(node.id), "NEUTRAL", null, colors, images, true);
                }
                ctx.restore();

                const rgba = ctx.getImageData(0, 0, columns, rows).data;
                for (let row = 0; row < rows; row++) {
                    let source = row * columns * 4;
                    let target = (row * width + left) * 3;
                    for (let column = 0; column < columns; column++) {
                        strip[target++] = rgba[source++];
                        strip[target++] = rgba[source++];
                        strip[target++] = rgba[source++];
                        source++;
                    }
                }
            }
            if (!(await bridge.pngRows(path, strip, rows))) throw new Error("PNG stream was rejected");
        }
        return await bridge.pngEnd(path);
    } catch (failure) {
        console.error("PNG export failed", failure);
        await bridge.pngAbort(path);
        return false;
    }
}
