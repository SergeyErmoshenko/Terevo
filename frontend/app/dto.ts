// Mirrors me/terevo/server/Dto.kt field for field. Keep in sync by hand - the shape is small
// enough that a generator would add more ceremony than it saves.

export type Gender = "MALE" | "FEMALE" | "UNKNOWN";
export type LayoutMode = "WHOLE_FAMILY" | "ANCESTORS" | "DESCENDANTS" | "BOTH";
export type LayoutDirection = "TOP_DOWN" | "LEFT_RIGHT";
export type LayoutDensity = "COMPACT" | "SPACIOUS";
export type ThemeMode = "LIGHT" | "DARK" | "SYSTEM";
export type MainTab = "TREE" | "PERSONS" | "EVENTS" | "DOCUMENTS";
export type ParentKind = "BIOLOGICAL" | "ADOPTIVE" | "STEP" | "FOSTER";
export type MarriageStatus = "MARRIED" | "DIVORCED" | "WIDOWED" | "PARTNERS";
export type RelationMode = "PARENT" | "CHILD" | "SPOUSE";
export type EventDateMode = "EXACT" | "APPROXIMATE" | "RANGE" | "UNKNOWN";
export type DatePrecision = "DAY" | "MONTH" | "YEAR";
export type EdgeStyle = "BIOLOGICAL" | "NON_BIOLOGICAL" | "MARRIAGE" | "DISSOLVED_MARRIAGE";

export interface PersonSummaryDto {
  id: string;
  name: string;
  gender: Gender;
  lifeYears: string;
}

export interface PersonDetailsDto {
  id: string;
  name: string;
  gender: Gender;
  lifeSpan: string;
  isAlive: boolean;
  maidenName: string;
  birthPlace: string | null;
  deathPlace: string | null;
  residence: string | null;
  occupation: string;
  notes: string;
  customFields: CustomFieldDto[];
  photoPath: string | null;
}

export interface CustomFieldDto {
  key: string;
  value: string;
}

export interface SpouseDto {
  person: PersonSummaryDto;
  details: string;
}

export interface RelatedPersonDto {
  person: PersonSummaryDto;
  role: string;
}

export interface MediaDto {
  id: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  thumbnailPath: string | null;
}

export interface SearchFilterDto {
  query: string;
  gender: Gender | null;
  birthYearFrom: number | null;
  birthYearTo: number | null;
  deathYearFrom: number | null;
  deathYearTo: number | null;
  place: string;
  hasParents: boolean | null;
  hasDates: boolean | null;
}

export const emptySearchFilter: SearchFilterDto = {
  query: "",
  gender: null,
  birthYearFrom: null,
  birthYearTo: null,
  deathYearFrom: null,
  deathYearTo: null,
  place: "",
  hasParents: null,
  hasDates: null,
};

export function isSearchFilterEmpty(filter: SearchFilterDto): boolean {
  return (
    filter.query.trim() === "" &&
    filter.gender === null &&
    filter.birthYearFrom === null &&
    filter.birthYearTo === null &&
    filter.deathYearFrom === null &&
    filter.deathYearTo === null &&
    filter.place.trim() === "" &&
    filter.hasParents === null &&
    filter.hasDates === null
  );
}

export interface EventDateInputDto {
  mode: EventDateMode;
  value: string;
  end: string;
  precision: DatePrecision;
}

export const emptyEventDateInput: EventDateInputDto = {
  mode: "UNKNOWN",
  value: "",
  end: "",
  precision: "YEAR",
};

export interface PersonFormFieldsDto {
  surname: string;
  givenName: string;
  patronymic: string;
  maidenName: string;
  gender: Gender;
  birth: EventDateInputDto;
  isAlive: boolean;
  death: EventDateInputDto;
  birthPlace: string;
  deathPlace: string;
  residence: string;
  occupation: string;
  notes: string;
  customFields: CustomFieldDto[];
}

export interface PersonFormDto {
  isNew: boolean;
  fields: PersonFormFieldsDto;
  requiredGender: Gender | null;
  customFieldSuggestions: string[];
  pendingMediaPaths: string[];
  blockingError: string | null;
  warnings: string[];
  isDiscardConfirmationVisible: boolean;
  canSave: boolean;
}

export interface RelationFieldsDto {
  query: string;
  selected: string | null;
  parentKind: ParentKind;
  marriageStatus: MarriageStatus;
  marriageSince: EventDateInputDto;
  marriagePlace: string;
  secondParent: string | null;
  secondParentQuery: string;
}

export interface RelationDialogDto {
  mode: RelationMode;
  source: PersonSummaryDto;
  fields: RelationFieldsDto;
  people: PersonSummaryDto[];
  secondParentCandidates: PersonSummaryDto[];
  error: string | null;
}

export interface KinshipDialogDto {
  source: PersonSummaryDto;
  query: string;
  target: string | null;
  term: string | null;
  people: PersonSummaryDto[];
}

export interface ParticipantDto {
  personId: string | null;
  query: string;
  role: string;
}

export interface EventFormFieldsDto {
  type: string;
  date: EventDateInputDto;
  place: string;
  notes: string;
  participants: ParticipantDto[];
}

export interface EventFormDto {
  isNew: boolean;
  fields: EventFormFieldsDto;
  people: PersonSummaryDto[];
  blockingError: string | null;
  canSave: boolean;
}

export interface DragRelationMenuDto {
  source: string;
  target: string;
  x: number;
  y: number;
  validity: Record<RelationMode, boolean>;
  sourceGender: Gender;
}

export interface GedcomPreviewDto {
  people: number;
  families: number;
  skippedTags: string[];
  error: string | null;
}

export interface MediaViewerDto {
  media: MediaDto;
  imagePath: string | null;
  pageCount: number;
  page: number;
  zoom: number;
}

export interface NamedCountDto {
  name: string;
  count: number;
}

export interface StatisticsDto {
  totalPersons: number;
  livingCount: number;
  deceasedCount: number;
  averageLifespanYears: number | null;
  lifespanSampleSize: number;
  generationDistribution: Record<string, number>;
  topSurnames: NamedCountDto[];
  topPlaces: NamedCountDto[];
  missingBirthDateCount: number;
  missingParentsCount: number;
}

export interface PersonRowDto {
  id: string;
  thumbnailPath: string | null;
  fullName: string;
  gender: Gender;
  birthDate: string;
  birthDateSortKey: string | null;
  residence: string;
  age: string;
  ageSortKey: number | null;
  occupation: string;
  comment: string;
  alive: boolean;
}

export interface EventRowDto {
  id: string | null;
  type: string;
  participants: string;
  date: string;
  dateSortKey: string | null;
  place: string;
  daysUntilAnniversary: number | null;
  yearsPassed: number | null;
  thumbnailPath: string | null;
}

export interface NodeDto {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface EdgeDto {
  from: string;
  to: string;
  union: boolean;
  style: EdgeStyle;
  points: number[];
}

export interface VisualDto {
  nameLines: string[];
  lifeYears: string;
  gender: Gender;
  thumbnailPath: string | null;
}

export interface LayoutDto {
  nodes: NodeDto[];
  edges: EdgeDto[];
  generations: Record<string, number>;
  visuals: Record<string, VisualDto>;
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

export interface CanvasDto {
  layoutVersion: number;
  layout: LayoutDto | null;
  selected: string | null;
  roles: Record<string, string>;
  searchResults: string[];
  homePersonId: string | null;
  centerOn: string | null;
  centerRequest: number;
}

export interface AppStateDto {
  isProjectOpen: boolean;
  projectName: string;
  projectDirectory: string | null;
  personCount: number;
  canvas: CanvasDto;
  layoutMode: LayoutMode;
  layoutDepth: number | null;
  layoutDirection: LayoutDirection;
  layoutDensity: LayoutDensity;
  searchFilter: SearchFilterDto;
  searchResults: PersonSummaryDto[];
  selectedPerson: PersonDetailsDto | null;
  selectedParents: PersonSummaryDto[];
  selectedChildren: PersonSummaryDto[];
  selectedSpouses: SpouseDto[];
  selectedMedia: MediaDto[];
  relatedPeople: RelatedPersonDto[];
  canGoBack: boolean;
  canGoForward: boolean;
  canUndo: boolean;
  canRedo: boolean;
  undoLabel: string;
  redoLabel: string;
  personForm: PersonFormDto | null;
  relationDialog: RelationDialogDto | null;
  dragRelationMenu: DragRelationMenuDto | null;
  kinshipDialog: KinshipDialogDto | null;
  statistics: StatisticsDto | null;
  gedcomPreview: GedcomPreviewDto | null;
  mediaViewer: MediaViewerDto | null;
  status: string;
  themeMode: ThemeMode;
  mainTab: MainTab;
  sidebarCollapsed: boolean;
  personRows: PersonRowDto[];
  eventRows: EventRowDto[];
  eventForm: EventFormDto | null;
  personViewOpen: boolean;
  dragSource: string | null;
  dragTargets: string[];
}

export const emptyAppState: AppStateDto = {
  isProjectOpen: false,
  projectName: "",
  projectDirectory: null,
  personCount: 0,
  canvas: {
    layoutVersion: 0,
    layout: null,
    selected: null,
    roles: {},
    searchResults: [],
    homePersonId: null,
    centerOn: null,
    centerRequest: 0,
  },
  layoutMode: "WHOLE_FAMILY",
  layoutDepth: null,
  layoutDirection: "TOP_DOWN",
  layoutDensity: "SPACIOUS",
  searchFilter: emptySearchFilter,
  searchResults: [],
  selectedPerson: null,
  selectedParents: [],
  selectedChildren: [],
  selectedSpouses: [],
  selectedMedia: [],
  relatedPeople: [],
  canGoBack: false,
  canGoForward: false,
  canUndo: false,
  canRedo: false,
  undoLabel: "Отменить",
  redoLabel: "Повторить",
  personForm: null,
  relationDialog: null,
  dragRelationMenu: null,
  kinshipDialog: null,
  statistics: null,
  gedcomPreview: null,
  mediaViewer: null,
  status: "Создайте или откройте проект",
  themeMode: "SYSTEM",
  mainTab: "TREE",
  sidebarCollapsed: false,
  personRows: [],
  eventRows: [],
  eventForm: null,
  personViewOpen: false,
  dragSource: null,
  dragTargets: [],
};
