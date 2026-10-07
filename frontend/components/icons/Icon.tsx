import type { JSX, SVGProps } from "react";

export type IconName =
  | "tree"
  | "people"
  | "events"
  | "documents"
  | "stats"
  | "search"
  | "settings"
  | "undo"
  | "redo"
  | "back"
  | "forward"
  | "fit"
  | "plus"
  | "minus"
  | "home"
  | "trash"
  | "photo"
  | "calendar"
  | "link"
  | "dots"
  | "externalLink"
  | "chevronUp"
  | "chevronDown"
  | "chevronLeft"
  | "chevronRight"
  | "filter"
  | "filterOff"
  | "mapPin"
  | "briefcase"
  | "fileText"
  | "gift"
  | "x"
  | "edit"
  | "check"
  | "paperclip"
  | "users"
  | "userPlus"
  | "panelRight"
  | "target";

const paths: Record<IconName, JSX.Element> = {
  tree: (
    <>
      <path d="M12 4v5M6 9h12M6 9v4m12-4v4M6 13H4m2 0h2m8 0h2m-2 0h2M4 13v5m4-5v5m8-5v5m4-5v5" />
      <circle cx="12" cy="4" r="1.5" />
    </>
  ),
  people: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path d="M3.5 20a5.5 5.5 0 0 1 11 0M16 5.5a3 3 0 0 1 0 5.8M17 15a5 5 0 0 1 3.5 4.8" />
    </>
  ),
  events: (
    <>
      <rect x="3" y="4" width="18" height="17" rx="2" />
      <path d="M16 2v4M8 2v4M3 9h18M7 13h3m4 0h3m-10 4h3" />
    </>
  ),
  documents: (
    <>
      <path d="M14 3v4a1 1 0 0 0 1 1h4" />
      <path d="M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" />
      <path d="M9 13h6M9 17h6" />
    </>
  ),
  stats: <path d="M4 20V10h4v10M10 20V4h4v16m2 0v-8h4v8M2 20h20" />,
  search: (
    <>
      <circle cx="10.8" cy="10.8" r="6.8" />
      <path d="m16 16 5 5" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="m19.4 15 .1.1 1.2 1-1.4 2.4-1.5-.5a7 7 0 0 1-1.7 1l-.3 1.6h-2.8l-.3-1.6a7 7 0 0 1-1.7-1l-1.5.5-1.4-2.4 1.2-1a7 7 0 0 1 0-2l-1.2-1 1.4-2.4 1.5.5a7 7 0 0 1 1.7-1l.3-1.6h2.8l.3 1.6a7 7 0 0 1 1.7 1l1.5-.5 1.4 2.4-1.2 1a7 7 0 0 1 0 2Z" />
    </>
  ),
  undo: (
    <>
      <path d="M9 14 4 9l5-5" />
      <path d="M4 9h10a6 6 0 0 1 0 12h-2" />
    </>
  ),
  redo: (
    <>
      <path d="m15 14 5-5-5-5" />
      <path d="M20 9H10a6 6 0 0 0 0 12h2" />
    </>
  ),
  back: <path d="m15 18-6-6 6-6" />,
  forward: <path d="m9 18 6-6-6-6" />,
  fit: <path d="M8 3H5a2 2 0 0 0-2 2v3m13-5h3a2 2 0 0 1 2 2v3M3 16v3a2 2 0 0 0 2 2h3m13-5v3a2 2 0 0 1-2 2h-3" />,
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  home: (
    <>
      <path d="M3 11.5 12 4l9 7.5" />
      <path d="M5 10v9a1 1 0 0 0 1 1h3v-6h6v6h3a1 1 0 0 0 1-1v-9" />
    </>
  ),
  trash: (
    <>
      <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m2 0-1 13a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1L6 7" />
      <path d="M10 11v6m4-6v6" />
    </>
  ),
  photo: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <circle cx="8.5" cy="9.5" r="1.6" />
      <path d="m4 17 5-5 4 4 3-3 4 4" />
    </>
  ),
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M16 3v4M8 3v4M3 10h18" />
    </>
  ),
  link: <path d="M9 15 15 9m-5-3 .8-.8a3.5 3.5 0 0 1 5 5l-.8.8m-7 1.9-.8.8a3.5 3.5 0 0 0 5 5l.8-.8" />,
  dots: (
    <>
      <circle cx="5" cy="12" r="1.4" />
      <circle cx="12" cy="12" r="1.4" />
      <circle cx="19" cy="12" r="1.4" />
    </>
  ),
  externalLink: (
    <>
      <path d="M14 4h6v6M20 4 10 14" />
      <path d="M18 14v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h5" />
    </>
  ),
  chevronUp: <path d="m6 15 6-6 6 6" />,
  chevronDown: <path d="m6 9 6 6 6-6" />,
  chevronLeft: <path d="m15 18-6-6 6-6" />,
  chevronRight: <path d="m9 18 6-6-6-6" />,
  filter: <path d="M4 5h16l-6.5 7.5V19l-3 2v-8.5Z" />,
  filterOff: (
    <>
      <path d="M4 5h4m5 0h7l-5 5.8M6.5 9.5 10.5 14v7l3-2v-3" />
      <path d="M3 3l18 18" />
    </>
  ),
  mapPin: (
    <>
      <path d="M12 21s7-6.7 7-11.5a7 7 0 1 0-14 0C5 14.3 12 21 12 21Z" />
      <circle cx="12" cy="9.5" r="2.3" />
    </>
  ),
  briefcase: (
    <>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18" />
    </>
  ),
  fileText: (
    <>
      <path d="M14 3v4a1 1 0 0 0 1 1h4" />
      <path d="M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" />
      <path d="M8 13h8M8 17h5" />
    </>
  ),
  gift: (
    <>
      <rect x="3" y="9" width="18" height="12" rx="1" />
      <path d="M3 13h18M12 9v12" />
      <path d="M12 9C9 9 8 7.5 8 6a2.5 2.5 0 0 1 4-2c1.3 0 1.8 1.2 2 2.5" />
      <path d="M12 9c3 0 4-1.5 4-3a2.5 2.5 0 0 0-4-2c-1.3 0-1.8 1.2-2 2.5" />
    </>
  ),
  x: <path d="M6 6l12 12M18 6 6 18" />,
  edit: (
    <>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </>
  ),
  check: <path d="m5 12 5 5 9-10" />,
  paperclip: <path d="M8.5 13.5 15 7a3 3 0 0 1 4.2 4.2l-8.9 8.9a4.5 4.5 0 1 1-6.4-6.4L12 5.5" />,
  users: (
    <>
      <circle cx="9" cy="8" r="3" />
      <circle cx="17" cy="10" r="2.5" />
      <path d="M3.5 20a5.5 5.5 0 0 1 11 0M14 20a4 4 0 0 1 6.8-3" />
    </>
  ),
  userPlus: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
      <path d="M18 8v6m3-3h-6" />
    </>
  ),
  panelRight: (
    <>
      <rect x="3" y="4" width="18" height="16" rx="2" />
      <path d="M15 4v16" />
    </>
  ),
  target: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <circle cx="12" cy="12" r="3.2" />
      <path d="M12 2v3.5M12 18.5V22M2 12h3.5M18.5 12H22" />
    </>
  ),
};

const common: SVGProps<SVGSVGElement> = {
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
};

export function Icon({ name, size }: { name: IconName; size?: number }) {
  return (
    <svg {...common} width={size ?? common.width} height={size ?? common.height}>
      {paths[name]}
    </svg>
  );
}
