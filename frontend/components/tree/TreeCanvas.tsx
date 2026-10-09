import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import type {
    MouseEvent as ReactMouseEvent,
    PointerEvent as ReactPointerEvent,
    WheelEvent as ReactWheelEvent
} from "react";
import type {Action, DispatchableAction} from "../../app/actions";
import type {AppStateDto, EdgeDto, LayoutDto, NodeDto, RelationMode} from "../../app/dto";
import {fileUrl} from "../../app/client";
import {Icon} from "../icons/Icon";
import {
    DEFAULT_SCALE,
    ZOOM_STEP,
    actualSize,
    center,
    defaultCamera,
    fit,
    pan,
    screenToWorld,
    worldToScreen,
    zoomAt,
    type Camera,
} from "./camera";
import {hitTestNode} from "./hitTest";

export type Accent = "NEUTRAL" | "FOCUSED" | "RELATED" | "MUTED";

interface Highlight {
    selected: string | null;
    roles: Record<string, string>;
    searchResults: Set<string>;
}

function isActive(h: Highlight): boolean {
    return h.selected != null || h.searchResults.size > 0;
}

function isRelated(h: Highlight, id: string): boolean {
    return id === h.selected || id in h.roles || h.searchResults.has(id);
}

function accentOf(h: Highlight, id: string): Accent {
    if (!isActive(h)) return "NEUTRAL";
    if (id === h.selected) return "FOCUSED";
    if (id in h.roles || h.searchResults.has(id)) return "RELATED";
    return "MUTED";
}

function roleOf(h: Highlight, id: string): string | null {
    if (!isActive(h)) return null;
    if (id === h.selected) return "Главный";
    return h.roles[id] ?? null;
}

function edgeAccent(h: Highlight, from: string, to: string): Accent {
    if (!isActive(h)) return "NEUTRAL";
    return isRelated(h, from) && isRelated(h, to) ? "RELATED" : "MUTED";
}

export function readThemeColors(element: HTMLElement) {
    const style = getComputedStyle(element);
    const v = (name: string) => style.getPropertyValue(name).trim();
    return {
        canvasBg: v("--canvas-bg"),
        bands: [v("--color-primary"), v("--color-neutral-gender"), v("--color-male"), v("--color-neutral-gender")],
        cardBg: v("--color-surface-raised"),
        cardBorder: v("--canvas-card-border"),
        bandBoost: Number.parseFloat(v("--canvas-band-boost")) || 1,
        shadow: "rgba(0,0,0,0.25)",
        textPrimary: v("--color-text"),
        textSecondary: v("--color-text-muted"),
        selection: v("--color-selection"),
        accent: v("--color-accent"),
        male: v("--color-male"),
        female: v("--color-female"),
        neutralGender: v("--color-neutral-gender"),
        edgeBlood: v("--color-edge-blood"),
        edgeOther: v("--color-edge-other"),
        danger: v("--color-danger"),
    };
}

type ThemeColors = ReturnType<typeof readThemeColors>;

function withAlpha(hex: string, alpha: number): string {
    const clean = hex.replace("#", "");
    if (clean.length !== 6) return hex;
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return `rgba(${r},${g},${b},${alpha})`;
}

const DETAILS_SCALE = 0.3;
const GENDER_STRIPE_WIDTH = 4;
const SHADOW_OFFSET = 3;
const TEXT_LEFT = 14;
const TEXT_RIGHT_MARGIN = 10;
const THUMBNAIL_SIZE = 68;
const THUMBNAIL_MARGIN = 8;
const TEXT_TOP = 8;
const NAME_LINE_GAP = 2;
const ROLE_GAP = 4;
const NAME_SIZE = 15;
const YEARS_SIZE = 12;
const ROLE_SIZE = 11;
const MUTED_ALPHA = 0.3;
const RELATED_ALPHA = 0.8;
const FIT_PADDING = 48;
const DRAG_THRESHOLD = 4;

function genderColor(colors: ThemeColors, gender: string): string {
    if (gender === "MALE") return colors.male;
    if (gender === "FEMALE") return colors.female;
    return colors.neutralGender;
}

function generationBands(layout: LayoutDto, vertical: boolean): { start: number; size: number }[] {
    // Grouped by generation, not by coordinate: cards of one generation have different sizes, so
    // their edges differ while the generation is still one band.
    const byGeneration = new Map<number, { start: number; end: number }>();
    for (const node of layout.nodes) {
        const generation = layout.generations[node.id];
        if (generation === undefined) continue;
        const start = vertical ? node.x : node.y;
        const end = start + (vertical ? node.width : node.height);
        const existing = byGeneration.get(generation);
        if (existing) {
            existing.start = Math.min(existing.start, start);
            existing.end = Math.max(existing.end, end);
        } else {
            byGeneration.set(generation, {start, end});
        }
    }
    return [...byGeneration.values()].sort((a, b) => a.start - b.start).map((band) => ({
        start: band.start,
        size: band.end - band.start
    }));
}

// Generations are rows in a top-down tree and columns in a left-to-right one, so the bands follow.
export function drawGenerationBands(
    ctx: CanvasRenderingContext2D,
    camera: Camera,
    layout: LayoutDto,
    colors: ThemeColors,
    viewport: { left: number; top: number; right: number; bottom: number },
    vertical: boolean,
) {
    generationBands(layout, vertical).forEach((band, index) => {
        const base = colors.bands[index % colors.bands.length];
        const alpha = (index % 2 === 0 ? 0.1 : 0.06) * colors.bandBoost;
        ctx.fillStyle = withAlpha(base, alpha);
        if (vertical) {
            const topLeft = worldToScreen(camera, {x: band.start, y: viewport.top});
            ctx.fillRect(topLeft.x, topLeft.y, band.size * camera.scale, (viewport.bottom - viewport.top) * camera.scale);
        } else {
            const topLeft = worldToScreen(camera, {x: viewport.left, y: band.start});
            ctx.fillRect(topLeft.x, topLeft.y, (viewport.right - viewport.left) * camera.scale, band.size * camera.scale);
        }
    });
}

export function drawEdge(ctx: CanvasRenderingContext2D, camera: Camera, edge: EdgeDto, accent: Accent, colors: ThemeColors) {
    const base = edge.style === "NON_BIOLOGICAL" ? colors.edgeOther : colors.edgeBlood;
    let color = base;
    if (accent === "FOCUSED" || accent === "RELATED") color = colors.selection;
    else if (accent === "MUTED") color = withAlpha(base, MUTED_ALPHA);
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.setLineDash(edge.style === "NON_BIOLOGICAL" || edge.style === "DISSOLVED_MARRIAGE" ? [8, 6] : []);
    ctx.beginPath();
    for (let i = 0; i + 3 < edge.points.length; i += 2) {
        const a = worldToScreen(camera, {x: edge.points[i], y: edge.points[i + 1]});
        const b = worldToScreen(camera, {x: edge.points[i + 2], y: edge.points[i + 3]});
        ctx.moveTo(a.x, a.y);
        ctx.lineTo(b.x, b.y);
    }
    ctx.stroke();
    ctx.setLineDash([]);
}

function nodeToRect(node: NodeDto) {
    return {left: node.x, top: node.y, width: node.width, height: node.height};
}

function roundRectPath(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + w, y, x + w, y + h, radius);
    ctx.arcTo(x + w, y + h, x, y + h, radius);
    ctx.arcTo(x, y + h, x, y, radius);
    ctx.arcTo(x, y, x + w, y, radius);
    ctx.closePath();
}

export function drawNode(
    ctx: CanvasRenderingContext2D,
    camera: Camera,
    node: NodeDto,
    visual: { nameLines: string[]; lifeYears: string; gender: string; thumbnailPath: string | null } | undefined,
    accent: Accent,
    role: string | null,
    colors: ThemeColors,
    images: Map<string, HTMLImageElement>,
    alwaysShowDetails = false,
    onImageLoad?: () => void,
) {
    const topLeft = worldToScreen(camera, {x: node.x, y: node.y});
    const width = node.width * camera.scale;
    const height = node.height * camera.scale;
    const radius = 8 * camera.scale;
    const contentAlpha = accent === "MUTED" ? MUTED_ALPHA : 1;

    if (accent !== "MUTED") {
        ctx.save();
        ctx.globalAlpha = 0.22;
        roundRectPath(ctx, topLeft.x, topLeft.y + SHADOW_OFFSET * camera.scale, width, height, radius);
        ctx.fillStyle = "#000000";
        ctx.fill();
        ctx.restore();
    }

    ctx.save();
    ctx.globalAlpha = contentAlpha;
    roundRectPath(ctx, topLeft.x, topLeft.y, width, height, radius);
    ctx.fillStyle = colors.cardBg;
    ctx.fill();
    ctx.strokeStyle = colors.cardBorder;
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.restore();

    const gColor = genderColor(colors, visual?.gender ?? "UNKNOWN");
    ctx.save();
    ctx.globalAlpha = contentAlpha;
    roundRectPath(ctx, topLeft.x, topLeft.y, width, height, radius);
    ctx.clip();
    ctx.fillStyle = gColor;
    ctx.fillRect(topLeft.x, topLeft.y, GENDER_STRIPE_WIDTH * camera.scale, height);
    ctx.restore();

    if (accent === "FOCUSED") {
        const halo = 10 * camera.scale;
        ctx.save();
        ctx.strokeStyle = withAlpha(colors.selection, 0.3);
        ctx.lineWidth = halo;
        roundRectPath(ctx, topLeft.x - halo / 2, topLeft.y - halo / 2, width + halo, height + halo, radius + halo / 2);
        ctx.stroke();
        ctx.restore();
        ctx.strokeStyle = colors.selection;
        ctx.lineWidth = 5 * camera.scale;
        roundRectPath(ctx, topLeft.x, topLeft.y, width, height, radius);
        ctx.stroke();
    } else if (accent === "RELATED") {
        ctx.save();
        ctx.globalAlpha = RELATED_ALPHA;
        ctx.strokeStyle = colors.selection;
        ctx.lineWidth = 2 * camera.scale;
        roundRectPath(ctx, topLeft.x, topLeft.y, width, height, radius);
        ctx.stroke();
        ctx.restore();
    }

    if ((camera.scale < DETAILS_SCALE && !alwaysShowDetails) || !visual) return;

    let textLeft = TEXT_LEFT * camera.scale;
    const url = fileUrl(visual.thumbnailPath);
    if (url && accent !== "MUTED") {
        let image = images.get(url);
        if (!image) {
            image = new Image();
            // The image decodes asynchronously: repaint once it is ready, otherwise the photo
            // would only show up after some unrelated redraw.
            image.onload = () => onImageLoad?.();
            image.src = url;
            images.set(url, image);
        }
        if (image.complete && image.naturalWidth > 0) {
            const size = THUMBNAIL_SIZE * camera.scale;
            const thumbX = topLeft.x + (GENDER_STRIPE_WIDTH + THUMBNAIL_MARGIN) * camera.scale;
            const thumbY = topLeft.y + (height - size) / 2;
            // Center-crop to a square so non-square photos are not stretched.
            const side = Math.min(image.naturalWidth, image.naturalHeight);
            const sx = (image.naturalWidth - side) / 2;
            const sy = (image.naturalHeight - side) / 2;
            ctx.save();
            ctx.beginPath();
            ctx.arc(thumbX + size / 2, thumbY + size / 2, size / 2, 0, Math.PI * 2);
            ctx.clip();
            ctx.drawImage(image, sx, sy, side, side, thumbX, thumbY, size, size);
            ctx.restore();
            ctx.save();
            ctx.globalAlpha = contentAlpha;
            ctx.strokeStyle = gColor;
            ctx.lineWidth = 2 * camera.scale;
            ctx.beginPath();
            ctx.arc(thumbX + size / 2, thumbY + size / 2, size / 2, 0, Math.PI * 2);
            ctx.stroke();
            ctx.restore();
            textLeft = thumbX - topLeft.x + size + THUMBNAIL_MARGIN * camera.scale;
        }
    }

    ctx.save();
    ctx.beginPath();
    ctx.rect(topLeft.x, topLeft.y, width, height);
    ctx.clip();
    ctx.globalAlpha = contentAlpha;
    ctx.textBaseline = "top";
    ctx.fillStyle = colors.textPrimary;
    ctx.font = `600 ${NAME_SIZE * camera.scale}px Inter, sans-serif`;
    const maxWidth = Math.max(0, width - textLeft - TEXT_RIGHT_MARGIN * camera.scale);
    let top = TEXT_TOP * camera.scale;
    const lineGap = NAME_LINE_GAP * camera.scale;
    for (const line of visual.nameLines) {
        ctx.fillText(line, topLeft.x + textLeft, topLeft.y + top, maxWidth);
        top += NAME_SIZE * camera.scale * 1.25 + lineGap;
    }
    ctx.fillStyle = colors.textSecondary;
    ctx.font = `400 ${YEARS_SIZE * camera.scale}px Inter, sans-serif`;
    ctx.fillText(visual.lifeYears, topLeft.x + textLeft, topLeft.y + top, maxWidth);
    ctx.restore();

    if (role) {
        ctx.save();
        ctx.fillStyle = colors.selection;
        ctx.font = `600 ${ROLE_SIZE * camera.scale}px Inter, sans-serif`;
        ctx.textBaseline = "bottom";
        ctx.fillText(role, topLeft.x, topLeft.y - ROLE_GAP * camera.scale, width);
        ctx.restore();
    }
}

export function TreeCanvas({state, dispatch}: {
    state: AppStateDto;
    dispatch: (action: DispatchableAction) => Promise<AppStateDto | null>
}) {
    const canvasRef = useRef<HTMLCanvasElement | null>(null);
    const containerRef = useRef<HTMLDivElement | null>(null);
    const cameraRef = useRef<Camera>(defaultCamera());
    const viewportRef = useRef({width: 0, height: 0});
    const everFittedRef = useRef(false);
    const lastCenterRequestRef = useRef(-1);
    const imagesRef = useRef(new Map<string, HTMLImageElement>());
    const pointerRef = useRef<{
        id: number;
        startScreen: { x: number; y: number };
        startWorld: { x: number; y: number };
        hitNodeId: string | null;
        moved: boolean;
        mode: "pan" | "node" | null;
    } | null>(null);
    const [zoomLabel, setZoomLabel] = useState("100%");
    const [contextMenu, setContextMenu] = useState<{ nodeId: string; x: number; y: number } | null>(null);
    const [pendingDeletion, setPendingDeletion] = useState<{ id: string; name: string } | null>(null);
    const [nodeDrag, setNodeDrag] = useState<{
        nodeId: string;
        current: { x: number; y: number };
        hoverTarget: string | null;
        hoverValid: boolean;
    } | null>(null);
    const dragTargetsRef = useRef<Set<string>>(new Set());

    const layout = state.canvas.layout;
    const nodeMap = useMemo(() => {
        const map = new Map<string, NodeDto>();
        layout?.nodes.forEach((node) => map.set(node.id, node));
        return map;
    }, [layout]);

    const nodeMapRef = useRef(nodeMap);
    nodeMapRef.current = nodeMap;
    const pendingCenterRef = useRef<string | null>(null);
    // Read the real size on demand: the ResizeObserver reports asynchronously, so viewportRef can
    // still be zero right after the canvas mounts (e.g. when switching back from another tab).
    const measureViewport = () => {
        const rect = containerRef.current?.getBoundingClientRect();
        if (rect && rect.width > 0 && rect.height > 0) viewportRef.current = {width: rect.width, height: rect.height};
        return viewportRef.current;
    };

    const highlight: Highlight = useMemo(
        () => ({
            selected: state.canvas.selected,
            roles: state.canvas.roles,
            searchResults: new Set(state.canvas.searchResults)
        }),
        [state.canvas.selected, state.canvas.roles, state.canvas.searchResults],
    );

    const draw = useCallback(() => {
        const canvas = canvasRef.current;
        const container = containerRef.current;
        if (!canvas || !container || !layout) return;
        const bounds = container.getBoundingClientRect();
        const ratio = window.devicePixelRatio || 1;
        const width = Math.max(1, Math.round(bounds.width * ratio));
        const height = Math.max(1, Math.round(bounds.height * ratio));
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        const colors = readThemeColors(container);
        ctx.fillStyle = colors.canvasBg;
        ctx.fillRect(0, 0, bounds.width, bounds.height);

        const camera = cameraRef.current;
        const topLeft = screenToWorld(camera, {x: -bounds.width, y: -bounds.height});
        const bottomRight = screenToWorld(camera, {x: bounds.width * 2, y: bounds.height * 2});
        const viewport = {
            left: topLeft.x,
            top: topLeft.y,
            right: bottomRight.x,
            bottom: bottomRight.y,
            width: bottomRight.x - topLeft.x
        };

        drawGenerationBands(ctx, camera, layout, colors, viewport, state.layoutDirection === "LEFT_RIGHT");

        for (const edge of layout.edges) {
            drawEdge(ctx, camera, edge, edgeAccent(highlight, edge.from, edge.to), colors);
        }
        for (const node of layout.nodes) {
            const visual = layout.visuals[node.id];
            drawNode(ctx, camera, node, visual, accentOf(highlight, node.id), roleOf(highlight, node.id), colors, imagesRef.current, false, () => drawRef.current());
        }

        if (nodeDrag) {
            const origin = nodeMap.get(nodeDrag.nodeId);
            if (origin) {
                const dx = nodeDrag.current.x - (pointerRef.current?.startWorld.x ?? nodeDrag.current.x);
                const dy = nodeDrag.current.y - (pointerRef.current?.startWorld.y ?? nodeDrag.current.y);
                const ghost = worldToScreen(camera, {x: origin.x + dx, y: origin.y + dy});
                ctx.save();
                ctx.globalAlpha = 0.4;
                ctx.strokeStyle = colors.accent;
                ctx.lineWidth = 2;
                roundRectPath(ctx, ghost.x, ghost.y, origin.width * camera.scale, origin.height * camera.scale, 8 * camera.scale);
                ctx.stroke();
                ctx.restore();
                if (nodeDrag.hoverTarget) {
                    const target = nodeMap.get(nodeDrag.hoverTarget);
                    if (target) {
                        const inset = 4;
                        const targetTopLeft = worldToScreen(camera, {x: target.x, y: target.y});
                        ctx.strokeStyle = nodeDrag.hoverValid ? colors.accent : withAlpha(colors.edgeBlood, MUTED_ALPHA);
                        ctx.lineWidth = 3;
                        roundRectPath(
                            ctx,
                            targetTopLeft.x - inset,
                            targetTopLeft.y - inset,
                            target.width * camera.scale + inset * 2,
                            target.height * camera.scale + inset * 2,
                            8 * camera.scale,
                        );
                        ctx.stroke();
                    }
                }
            }
        }

        setZoomLabel(`${Math.round(camera.scale * 100)}%`);
    }, [layout, highlight, nodeDrag, nodeMap, state.layoutDirection]);

    const drawRef = useRef(draw);
    drawRef.current = draw;

    useEffect(() => {
        draw();
    }, [draw]);

    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;
        const observer = new ResizeObserver((entries) => {
            const entry = entries[0];
            if (!entry) return;
            const {width, height} = entry.contentRect;
            viewportRef.current = {width, height};
            if (!everFittedRef.current && layout && layout.nodes.length > 0 && width > 0 && height > 0) {
                const bounds = {
                    left: layout.minX,
                    top: layout.minY,
                    width: layout.maxX - layout.minX,
                    height: layout.maxY - layout.minY
                };
                cameraRef.current = fit(bounds, {width, height}, FIT_PADDING);
                everFittedRef.current = true;
                // A centering request that arrived before the canvas had a size wins over the initial fit.
                const pending = pendingCenterRef.current ? nodeMapRef.current.get(pendingCenterRef.current) : undefined;
                pendingCenterRef.current = null;
                if (pending) cameraRef.current = center({
                    ...cameraRef.current,
                    scale: DEFAULT_SCALE
                }, nodeToRect(pending), {width, height});
            }
            draw();
        });
        observer.observe(container);
        return () => observer.disconnect();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [draw, layout != null]);

    // Selecting someone from search, a table row, or undo/redo bumps centerRequest on the backend;
    // the canvas reacts by recentering without the backend ever needing to know about the camera.
    useEffect(() => {
        if (state.canvas.centerRequest === lastCenterRequestRef.current) return;
        lastCenterRequestRef.current = state.canvas.centerRequest;
        const target = state.canvas.centerOn;
        if (!target) return;
        const node = nodeMap.get(target);
        if (!node) return;
        const size = measureViewport();
        if (size.width <= 0 || size.height <= 0) {
            pendingCenterRef.current = target;
            return;
        }
        cameraRef.current = center(cameraRef.current, nodeToRect(node), size);
        // Do not let the one-time initial fit undo this centering on a freshly mounted canvas.
        everFittedRef.current = true;
        draw();
    }, [state.canvas.centerRequest, state.canvas.centerOn, nodeMap, draw]);

    const selectedId = state.selectedPerson?.id ?? null;
    // One "locate" button: with someone selected it goes to them (search result, table row, click),
    // otherwise to the tree's anchor person, otherwise it fits the whole tree.
    const locate = () => {
        const size = measureViewport();
        const selectedNode = selectedId ? nodeMap.get(selectedId) : undefined;
        if (selectedNode) {
            cameraRef.current = center(actualSize(cameraRef.current), nodeToRect(selectedNode), size);
            draw();
            return;
        }
        if (selectedId) {
            // Selected but not in the current layout (filtered out or beyond the depth limit): ask the backend.
            void dispatch({type: "selectPerson", id: selectedId} satisfies Action);
            return;
        }
        const home = state.canvas.homePersonId;
        const homeNode = home ? nodeMap.get(home) : undefined;
        if (homeNode) {
            cameraRef.current = center(actualSize(cameraRef.current), nodeToRect(homeNode), size);
        } else if (layout) {
            const bounds = {
                left: layout.minX,
                top: layout.minY,
                width: layout.maxX - layout.minX,
                height: layout.maxY - layout.minY
            };
            cameraRef.current = fit(bounds, size, FIT_PADDING);
        }
        draw();
    };

    const zoomButton = (factor: number) => {
        const {width, height} = viewportRef.current;
        cameraRef.current = zoomAt(cameraRef.current, {x: width / 2, y: height / 2}, factor);
        draw();
    };

    const onWheel = (event: ReactWheelEvent<HTMLCanvasElement>) => {
        event.preventDefault();
        const rect = event.currentTarget.getBoundingClientRect();
        const point = {x: event.clientX - rect.left, y: event.clientY - rect.top};
        const factor = event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
        cameraRef.current = zoomAt(cameraRef.current, point, factor);
        draw();
    };

    const hitAt = (clientX: number, clientY: number): NodeDto | undefined => {
        const rect = containerRef.current?.getBoundingClientRect();
        if (!rect || !layout) return undefined;
        const world = screenToWorld(cameraRef.current, {x: clientX - rect.left, y: clientY - rect.top});
        return hitTestNode(layout.nodes, world);
    };

    const onPointerDown = (event: ReactPointerEvent<HTMLCanvasElement>) => {
        if (event.button !== 0) return;
        event.currentTarget.setPointerCapture(event.pointerId);
        const rect = event.currentTarget.getBoundingClientRect();
        const world = screenToWorld(cameraRef.current, {x: event.clientX - rect.left, y: event.clientY - rect.top});
        const hit = hitTestNode(layout?.nodes ?? [], world);
        pointerRef.current = {
            id: event.pointerId,
            startScreen: {x: event.clientX, y: event.clientY},
            startWorld: world,
            hitNodeId: hit?.id ?? null,
            moved: false,
            mode: null,
        };
    };

    const onPointerMove = async (event: ReactPointerEvent<HTMLCanvasElement>) => {
        const drag = pointerRef.current;
        if (!drag || drag.id !== event.pointerId) return;
        const dx = event.clientX - drag.startScreen.x;
        const dy = event.clientY - drag.startScreen.y;
        if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
        if (!drag.moved) {
            drag.moved = true;
            drag.mode = drag.hitNodeId ? "node" : "pan";
            if (drag.mode === "node" && drag.hitNodeId) {
                const response = await dispatch({type: "startDrag", source: drag.hitNodeId} satisfies Action);
                dragTargetsRef.current = new Set(response?.dragTargets ?? []);
            }
        }
        const rect = event.currentTarget.getBoundingClientRect();
        if (drag.mode === "node" && drag.hitNodeId) {
            const world = screenToWorld(cameraRef.current, {x: event.clientX - rect.left, y: event.clientY - rect.top});
            const hit = hitTestNode(layout?.nodes ?? [], world);
            const hoverTarget = hit && hit.id !== drag.hitNodeId ? hit.id : null;
            setNodeDrag({
                nodeId: drag.hitNodeId,
                current: world,
                hoverTarget,
                hoverValid: hoverTarget ? dragTargetsRef.current.has(hoverTarget) : false,
            });
        } else if (drag.mode === "pan") {
            cameraRef.current = pan(cameraRef.current, {x: dx, y: dy});
            drag.startScreen = {x: event.clientX, y: event.clientY};
            draw();
        }
    };

    const onPointerUp = async (event: ReactPointerEvent<HTMLCanvasElement>) => {
        const drag = pointerRef.current;
        pointerRef.current = null;
        if (!drag || drag.id !== event.pointerId) return;
        if (!drag.moved) {
            await dispatch({type: "selectOnCanvas", id: drag.hitNodeId} satisfies Action);
            return;
        }
        if (drag.mode === "node" && drag.hitNodeId) {
            const hoverTarget = nodeDrag?.hoverTarget ?? null;
            setNodeDrag(null);
            await dispatch({
                type: "dropDrag",
                target: hoverTarget,
                x: event.clientX,
                y: event.clientY
            } satisfies Action);
        }
    };

    const onDoubleClick = async (event: ReactMouseEvent<HTMLCanvasElement>) => {
        const hit = hitAt(event.clientX, event.clientY);
        if (hit) await dispatch({type: "editPerson", id: hit.id} satisfies Action);
    };

    const onContextMenu = (event: ReactMouseEvent<HTMLCanvasElement>) => {
        event.preventDefault();
        const hit = hitAt(event.clientX, event.clientY);
        if (hit) {
            setContextMenu({nodeId: hit.id, x: event.clientX, y: event.clientY});
        } else {
            void dispatch({type: "addPerson"} satisfies Action);
        }
    };

    const menuPerson = contextMenu ? layout?.visuals[contextMenu.nodeId] : undefined;
    const menuGender = menuPerson?.gender ?? "UNKNOWN";
    const spouseLabel = menuGender === "MALE" ? "Добавить супругу" : "Добавить супруга";

    const addRelativeFromMenu = (mode: RelationMode) => {
        if (!contextMenu) return;
        void dispatch({type: "addRelative", mode, id: contextMenu.nodeId} satisfies Action);
        setContextMenu(null);
    };

    return (
        <div className="tree-stage" ref={containerRef}>
            <canvas
                ref={canvasRef}
                className="tree-canvas"
                aria-label="Семейное древо"
                onWheel={onWheel}
                onPointerDown={onPointerDown}
                onPointerMove={onPointerMove}
                onPointerUp={onPointerUp}
                onDoubleClick={onDoubleClick}
                onContextMenu={onContextMenu}
            />
            {layout && (
                <div className="canvas-locate">
                    <button
                        className="secondary-button"
                        type="button"
                        title={selectedId ? "Перейти к выбранному человеку" : "Перейти к главному человеку древа"}
                        onClick={locate}
                    >
                        <Icon name={selectedId ? "target" : "home"}/>
                        {selectedId ? "К выбранному" : "К главному"}
                    </button>
                </div>
            )}
            <div className="canvas-controls">
                <button className="icon-button" type="button" aria-label="Уменьшить"
                        onClick={() => zoomButton(1 / 1.15)}>
                    <Icon name="minus"/>
                </button>
                <span>{zoomLabel}</span>
                <button className="icon-button" type="button" aria-label="Увеличить" onClick={() => zoomButton(1.15)}>
                    <Icon name="plus"/>
                </button>
                <span className="control-divider"/>
                <button
                    className="icon-button"
                    type="button"
                    aria-label="Реальный размер"
                    onClick={() => {
                        const size = measureViewport();
                        const selectedNode = selectedId ? nodeMap.get(selectedId) : undefined;
                        if (selectedNode) {
                            // Keep the selected person in the middle of the view while the scale resets.
                            cameraRef.current = center(actualSize(cameraRef.current), nodeToRect(selectedNode), size);
                        } else {
                            // Nobody selected: zoom around the middle of the screen, not the tree's origin.
                            const current = cameraRef.current;
                            cameraRef.current = zoomAt(current, {
                                x: size.width / 2,
                                y: size.height / 2
                            }, 1 / current.scale);
                        }
                        draw();
                    }}
                >
                    100%
                </button>
                <button
                    className="icon-button"
                    type="button"
                    aria-label="Вписать в экран"
                    onClick={() => {
                        if (!layout) return;
                        const bounds = {
                            left: layout.minX,
                            top: layout.minY,
                            width: layout.maxX - layout.minX,
                            height: layout.maxY - layout.minY
                        };
                        cameraRef.current = fit(bounds, viewportRef.current, FIT_PADDING);
                        draw();
                    }}
                >
                    <Icon name="fit"/>
                </button>
            </div>
            {contextMenu && (
                <>
                    <div className="context-menu-backdrop" onMouseDown={() => setContextMenu(null)}/>
                    <div className="context-menu" style={{left: contextMenu.x, top: contextMenu.y}}>
                        <button type="button" onClick={() => addRelativeFromMenu("PARENT")}>
                            Добавить родителя
                        </button>
                        <button type="button" onClick={() => addRelativeFromMenu("CHILD")}>
                            Добавить ребёнка
                        </button>
                        <button type="button" onClick={() => addRelativeFromMenu("SPOUSE")}>
                            {spouseLabel}
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                void dispatch({type: "viewPerson", id: contextMenu.nodeId} satisfies Action);
                                setContextMenu(null);
                            }}
                        >
                            Просмотреть
                        </button>
                        <hr/>
                        <button
                            type="button"
                            className="danger"
                            onClick={() => {
                                const name = menuPerson?.nameLines.join(" ") ?? "";
                                setPendingDeletion({id: contextMenu.nodeId, name});
                                setContextMenu(null);
                            }}
                        >
                            Удалить
                        </button>
                    </div>
                </>
            )}
            {pendingDeletion && (
                <div className="modal-backdrop" onMouseDown={() => setPendingDeletion(null)}>
                    <section className="dialog dialog-sm" role="alertdialog">
                        <header className="dialog-header">
                            <div>
                                <h2>Удалить человека?</h2>
                            </div>
                        </header>
                        <div className="dialog-body">
                            <p className="dialog-plain-text">
                                {pendingDeletion.name} будет удалён из дерева вместе со всеми его связями. Действие
                                можно отменить через «Правка → Отменить».
                            </p>
                        </div>
                        <footer className="dialog-footer">
                            <button className="secondary-button" type="button" onClick={() => setPendingDeletion(null)}>
                                Отмена
                            </button>
                            <button
                                className="primary-button danger-button"
                                type="button"
                                onClick={() => {
                                    void dispatch({type: "deletePerson", id: pendingDeletion.id} satisfies Action);
                                    setPendingDeletion(null);
                                }}
                            >
                                Удалить
                            </button>
                        </footer>
                    </section>
                </div>
            )}
            {!layout && (
                <div className="empty-state minimal">
                    {state.isProjectOpen && (
                        <div className="empty-card">
                            <h2>В проекте пока нет людей</h2>
                            <p>Добавьте первого человека — от него начнётся древо.</p>
                            <div className="empty-actions">
                                <button className="primary-button" type="button"
                                        onClick={() => dispatch({type: "addPerson"})}>
                                    <Icon name="userPlus"/>
                                    Добавить человека
                                </button>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
