export type Severity = 'green' | 'amber' | 'red';
export type AlertState = 'open' | 'ack' | 'suppressed' | 'resolved';
export type DateFilterMode = 'today' | 'day' | 'all';

export interface DateFilterState {
  mode: DateFilterMode;
  day?: string;
}

export interface WorkflowNode {
  key: string;
  eventType: string;
  start?: boolean;
  terminal?: boolean;
}

export interface WorkflowEdge {
  from: string;
  to: string;
  maxLatencySec?: number;
  absoluteDeadline?: string;
  expectedCount?: number;
  severity?: Severity;
  optional?: boolean;
}

export interface WorkflowGraph {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  groupDimensions?: string[];
}

export interface WorkflowSummary {
  id: string;
  key: string;
  name: string;
  status?: Severity;
  activeVersion?: string;
  graph?: WorkflowGraph;
  groupDimensions?: string[];
}

export interface WallboardCountdown {
  label: string;
  dueAt: string;
  remainingSec: number;
  severity: Severity;
}

export interface StageAggregate {
  workflowVersionId: string;
  workflowId?: string;
  groupHash?: string;
  nodeKey: string;
  bucketStart?: string;
  inFlight: number;
  completed: number;
  late: number;
  failed: number;
}

export interface WallboardGroupTile {
  label: string;
  groupHash: string;
  status: Severity;
  inFlight: number;
  late: number;
  failed: number;
  countdowns: WallboardCountdown[];
}

export interface WallboardWorkflowTile {
  workflowId: string;
  workflowKey: string;
  name: string;
  status: Severity;
  alerts: AlertSummary[];
  groups: WallboardGroupTile[];
}

export interface AlertSummary {
  id: string;
  nodeKey: string;
  severity: Severity;
  state: AlertState;
  title: string;
  correlationKey?: string;
  triggeredAt?: string;
  lastTriggeredAt?: string;
  reason?: string;
}

export interface Alert extends AlertSummary {
  dedupeKey?: string;
  suppressedUntil?: string;
  ackedBy?: string;
  ackedAt?: string;
}

export interface ExpectationView {
  from: string;
  to: string;
  dueAt: string;
  severity: Severity;
  status?: string;
  remainingSec?: number;
}

export interface ItemEvent {
  node: string;
  eventTime: string;
  receivedAt: string;
  late?: boolean;
  orderViolation?: boolean;
  payloadExcerpt?: string;
  durationMs?: number;
}

export interface ItemTimeline {
  workflowId: string;
  workflowKey?: string;
  workflowName?: string;
  workflowVersionId?: string;
  correlationKey: string;
  currentStage?: string;
  group?: Record<string, string>;
  groupHash?: string;
  groupLabel?: string;
  startedAt?: string;
  updatedAt?: string;
  status: Severity;
  events: ItemEvent[];
  pendingExpectations: ExpectationView[];
  alerts?: AlertSummary[];
}

export interface CreateWorkflowRequest {
  name: string;
  key: string;
  createdBy: string;
  graph: WorkflowGraph;
}

export interface IngestRequest {
  eventId?: string;
  sourceSystem: string;
  eventType: string;
  eventTime: string;
  correlationKey: string;
  workflowKey?: string;
  group?: Record<string, string>;
  payload?: Record<string, unknown>;
}

export interface AckRequest {
  reason?: string;
  ticket?: string;
  until?: string;
}

export interface WallboardView {
  workflows: WallboardWorkflowTile[];
  updatedAt: string;
}

export interface WorkflowInstance {
  correlationId: string;
  workflowVersionId?: string;
  workflowId?: string;
  workflowKey?: string;
  workflowName?: string;
  status: Severity;
  currentStage?: string;
  startedAt?: string;
  updatedAt?: string;
  lastEventAt?: string;
  groupHash?: string;
  groupLabel?: string;
  late?: boolean;
  orderViolation?: boolean;
}

export interface WorkflowInstancePage {
  items: WorkflowInstance[];
  page: number;
  size: number;
  hasMore: boolean;
}
