import { Injectable } from '@angular/core';
import {
  AckRequest,
  Alert,
  AlertSummary,
  CreateWorkflowRequest,
  IngestRequest,
  ItemTimeline,
  StageAggregate,
  WallboardGroupTile,
  WallboardView,
  WallboardWorkflowTile,
  WorkflowGraph,
  WorkflowSummary
} from '../models';

@Injectable({ providedIn: 'root' })
export class MockBackendService {
  private readonly workflows: WorkflowSummary[] = [];
  private readonly wallboard: WallboardWorkflowTile[] = [];
  private readonly aggregates: Record<string, StageAggregate[]> = {};
  private readonly itemTimelines: Map<string, ItemTimeline> = new Map();
  private readonly alerts: Map<string, Alert> = new Map();

  constructor() {
    this.seed();
  }

  getWallboard(): WallboardView {
    return {
      workflows: this.wallboard,
      updatedAt: new Date().toISOString()
    };
  }

  getWorkflows(): WorkflowSummary[] {
    return this.workflows;
  }

  getWorkflow(key: string): WorkflowSummary | undefined {
    return this.workflows.find((wf) => wf.key === key);
  }

  getAggregates(workflowId: string, groupHash?: string): StageAggregate[] {
    const agg = this.aggregates[workflowId] ?? [];
    if (!groupHash) {
      return agg;
    }
    return agg.filter((row) => row.groupHash === groupHash);
  }

  getItem(correlationKey: string): ItemTimeline | undefined {
    return this.itemTimelines.get(correlationKey);
  }

  getAlerts(state?: string): Alert[] {
    const allAlerts = Array.from(this.alerts.values());
    if (!state) {
      return allAlerts;
    }
    return allAlerts.filter((alert) => alert.state === state);
  }

  ackAlert(id: string, payload?: AckRequest): Alert | undefined {
    const alert = this.alerts.get(id);
    if (!alert) {
      return undefined;
    }
    alert.state = 'ack';
    alert.ackedAt = new Date().toISOString();
    alert.ackedBy = 'playwright';
    alert.reason = payload?.reason ?? alert.reason;
    this.alerts.set(id, alert);
    return alert;
  }

  suppressAlert(id: string, payload?: AckRequest): Alert | undefined {
    const alert = this.alerts.get(id);
    if (!alert) {
      return undefined;
    }
    alert.state = 'suppressed';
    alert.suppressedUntil = payload?.until ?? new Date(Date.now() + 15 * 60 * 1000).toISOString();
    alert.reason = payload?.reason ?? alert.reason;
    this.alerts.set(id, alert);
    return alert;
  }

  resolveAlert(id: string, payload?: AckRequest): Alert | undefined {
    const alert = this.alerts.get(id);
    if (!alert) {
      return undefined;
    }
    alert.state = 'resolved';
    alert.reason = payload?.reason ?? alert.reason;
    alert.lastTriggeredAt = new Date().toISOString();
    this.alerts.set(id, alert);
    return alert;
  }

  createWorkflow(payload: CreateWorkflowRequest): WorkflowSummary {
    const existing = this.workflows.find((wf) => wf.key === payload.key);
    if (existing) {
      return existing;
    }
    const workflow: WorkflowSummary = {
      id: (this.workflows.length + 1).toString(),
      key: payload.key,
      name: payload.name,
      status: 'green',
      activeVersion: 'v1',
      graph: payload.graph,
      groupDimensions: payload.graph.groupDimensions
    };
    this.workflows.push(workflow);
    this.wallboard.push({
      workflowId: workflow.id,
      workflowKey: workflow.key,
      name: workflow.name,
      status: 'green',
      alerts: [],
      groups: [
        this.buildGroup('default', 'default', 'green', 3, 0, 0, payload.graph)
      ]
    });
    this.aggregates[workflow.id] = payload.graph.nodes.map((node) => ({
      workflowVersionId: `${workflow.id}-v1`,
      nodeKey: node.key,
      groupHash: 'default',
      inFlight: 2,
      completed: 5,
      late: 0,
      failed: 0
    }));
    return workflow;
  }

  ingest(payload: IngestRequest) {
    const now = new Date();
    const receivedAt = now.toISOString();
    const eventTime = payload.eventTime || receivedAt;
    const itemKey = payload.correlationKey;
    const workflow =
      this.workflows.find((wf) => wf.key === payload.workflowKey) ?? this.workflows[0];
    const groupHash = this.buildGroupHash(payload.group);
    const groupLabel = this.buildGroupLabel(payload.group);
    const timeline = this.itemTimelines.get(itemKey) ?? {
      workflowId: workflow.key,
      workflowVersionId: `${workflow.id}-v1`,
      correlationKey: itemKey,
      status: 'green',
      events: [],
      pendingExpectations: [],
      group: payload.group ?? { book: 'EQD', region: 'NY' }
    };
    timeline.events = [
      ...timeline.events,
      {
        node: payload.eventType,
        eventTime,
        receivedAt,
        late: false,
        orderViolation: false
      }
    ];
    this.itemTimelines.set(itemKey, timeline);
    this.bumpWallboard(workflow, groupHash, groupLabel, payload.eventType);
    this.bumpAggregates(workflow, groupHash, payload.eventType);
    return {
      ...payload,
      eventTime,
      receivedAt,
      normalized: true
    };
  }

  private bumpWallboard(
    workflow: WorkflowSummary,
    groupHash: string,
    groupLabel: string,
    eventType: string
  ) {
    const card = this.wallboard.find((wf) => wf.workflowKey === workflow.key);
    if (!card) {
      return;
    }
    let group = card.groups.find((g) => g.groupHash === groupHash);
    if (!group) {
      group = this.buildGroup(groupLabel, groupHash, 'green', 0, 0, 0, workflow.graph!);
      card.groups.push(group);
    }
    group.inFlight += 1;
    group.status = group.inFlight > 10 ? 'red' : 'amber';
    card.status = group.status;
    const countdown = group.countdowns?.[0];
    if (countdown) {
      countdown.remainingSec = Math.max(0, (countdown.remainingSec ?? 0) - 30);
    }
    const wallboardAlert: AlertSummary = {
      id: `alrt-${Date.now()}`,
      nodeKey: eventType.toLowerCase(),
      severity: group.status,
      state: 'open',
      title: `${eventType} accepted`,
      correlationKey: groupHash,
      triggeredAt: new Date().toISOString()
    };
    card.alerts = [...card.alerts, wallboardAlert].slice(-5);
  }

  private bumpAggregates(workflow: WorkflowSummary, groupHash: string, eventType: string) {
    const aggregates = (this.aggregates[workflow.id] = this.aggregates[workflow.id] ?? []);
    const nodeKey =
      workflow.graph?.nodes.find((n) => n.eventType === eventType)?.key ??
      workflow.graph?.nodes.find((n) => n.key === eventType)?.key ??
      eventType.toLowerCase();
    let aggregate = aggregates.find((agg) => agg.nodeKey === nodeKey && agg.groupHash === groupHash);
    if (!aggregate) {
      aggregate = {
        workflowVersionId: `${workflow.id}-v1`,
        workflowId: workflow.id,
        groupHash,
        nodeKey,
        inFlight: 0,
        completed: 0,
        late: 0,
        failed: 0
      };
      aggregates.push(aggregate);
    }
    aggregate.inFlight += 1;
  }

  private buildGroupHash(group?: Record<string, string>) {
    if (!group || Object.keys(group).length === 0) {
      return 'default';
    }
    return Object.values(group)
      .join('-')
      .toLowerCase()
      .replace(/\s+/g, '-');
  }

  private buildGroupLabel(group?: Record<string, string>) {
    if (!group || Object.keys(group).length === 0) {
      return 'default';
    }
    return Object.values(group)
      .map((v) => v.toString().toUpperCase())
      .join(' / ');
  }

  private seed() {
    const tradeGraph: WorkflowGraph = {
      nodes: [
        { key: 'ingest', eventType: 'TRADE_INGEST', start: true },
        { key: 'sys2-verify', eventType: 'SYS2_VERIFIED' },
        { key: 'sys3-ack', eventType: 'SYS3_ACK' },
        { key: 'sys4-settle', eventType: 'SYS4_SETTLED', terminal: true }
      ],
      edges: [
        { from: 'ingest', to: 'sys2-verify', maxLatencySec: 300, severity: 'amber', expectedCount: 2 },
        { from: 'sys2-verify', to: 'sys3-ack', maxLatencySec: 300, severity: 'red' },
        { from: 'sys3-ack', to: 'sys4-settle', absoluteDeadline: '08:00Z', severity: 'amber', optional: true }
      ],
      groupDimensions: ['book', 'region']
    };
    const fileGraph: WorkflowGraph = {
      nodes: [
        { key: 'file-received', eventType: 'FILE_RECEIVED', start: true },
        { key: 'validated', eventType: 'FILE_VALIDATED' },
        { key: 'loaded', eventType: 'FILE_LOADED', terminal: true }
      ],
      edges: [
        { from: 'file-received', to: 'validated', absoluteDeadline: '08:00Z', severity: 'red' },
        { from: 'validated', to: 'loaded', maxLatencySec: 900, severity: 'amber', expectedCount: 1 }
      ],
      groupDimensions: ['feed', 'region']
    };

    this.workflows.push(
      {
        id: 'wf-trade',
        key: 'trade-lifecycle',
        name: 'Trade Lifecycle',
        status: 'amber',
        activeVersion: 'v2',
        graph: tradeGraph,
        groupDimensions: tradeGraph.groupDimensions
      },
      {
        id: 'wf-file',
        key: 'file-receipt',
        name: 'File Receipt',
        status: 'green',
        activeVersion: 'v1',
        graph: fileGraph,
        groupDimensions: fileGraph.groupDimensions
      }
    );

    this.wallboard.push(
      {
        workflowId: 'wf-trade',
        workflowKey: 'trade-lifecycle',
        name: 'Trade Lifecycle',
        status: 'amber',
        alerts: [],
        groups: [
          this.buildGroup('EQD / NY', 'eqd-ny', 'amber', 9, 2, 1, tradeGraph),
          this.buildGroup('FI / LN', 'fi-ln', 'green', 7, 0, 0, tradeGraph)
        ]
      },
      {
        workflowId: 'wf-file',
        workflowKey: 'file-receipt',
        name: 'File Receipt',
        status: 'green',
        alerts: [],
        groups: [this.buildGroup('DAILY_FEED', 'daily-feed', 'green', 3, 0, 0, fileGraph)]
      }
    );

    this.aggregates['wf-trade'] = [
      {
        workflowVersionId: 'wf-trade-v2',
        workflowId: 'wf-trade',
        groupHash: 'eqd-ny',
        nodeKey: 'ingest',
        inFlight: 2,
        completed: 18,
        late: 0,
        failed: 0
      },
      {
        workflowVersionId: 'wf-trade-v2',
        workflowId: 'wf-trade',
        groupHash: 'eqd-ny',
        nodeKey: 'sys2-verify',
        inFlight: 3,
        completed: 15,
        late: 1,
        failed: 0
      },
      {
        workflowVersionId: 'wf-trade-v2',
        workflowId: 'wf-trade',
        groupHash: 'eqd-ny',
        nodeKey: 'sys3-ack',
        inFlight: 4,
        completed: 14,
        late: 1,
        failed: 1
      },
      {
        workflowVersionId: 'wf-trade-v2',
        workflowId: 'wf-trade',
        groupHash: 'eqd-ny',
        nodeKey: 'sys4-settle',
        inFlight: 1,
        completed: 12,
        late: 2,
        failed: 0
      }
    ];
    this.aggregates['wf-file'] = [
      {
        workflowVersionId: 'wf-file-v1',
        workflowId: 'wf-file',
        groupHash: 'daily-feed',
        nodeKey: 'file-received',
        inFlight: 1,
        completed: 5,
        late: 0,
        failed: 0
      },
      {
        workflowVersionId: 'wf-file-v1',
        workflowId: 'wf-file',
        groupHash: 'daily-feed',
        nodeKey: 'validated',
        inFlight: 0,
        completed: 5,
        late: 0,
        failed: 0
      },
      {
        workflowVersionId: 'wf-file-v1',
        workflowId: 'wf-file',
        groupHash: 'daily-feed',
        nodeKey: 'loaded',
        inFlight: 0,
        completed: 5,
        late: 0,
        failed: 0
      }
    ];

    const correlationKey = 'TR123';
    this.itemTimelines.set(correlationKey, {
      workflowId: 'trade-lifecycle',
      workflowVersionId: 'wf-trade-v2',
      correlationKey,
      status: 'amber',
      group: { book: 'EQD', region: 'NY' },
      events: [
        {
          node: 'ingest',
          eventTime: this.offset(-12),
          receivedAt: this.offset(-12),
          late: false,
          orderViolation: false
        },
        {
          node: 'sys2-verify',
          eventTime: this.offset(-10),
          receivedAt: this.offset(-10),
          late: false,
          orderViolation: false
        }
      ],
      pendingExpectations: [
        { from: 'sys2-verify', to: 'sys3-ack', dueAt: this.offset(2), severity: 'red', remainingSec: 120 }
      ],
      alerts: [
        {
          id: 'alrt-1',
          nodeKey: 'sys3-ack',
          severity: 'red',
          state: 'open',
          title: 'SYS3 ACK pending',
          correlationKey,
          triggeredAt: this.offset(-2),
          lastTriggeredAt: this.offset(-1),
          reason: 'SLA_MISSED'
        }
      ]
    });

    const alert: Alert = {
      id: 'alrt-1',
      nodeKey: 'sys3-ack',
      severity: 'red',
      state: 'open',
      title: 'SYS3 ACK pending',
      correlationKey,
      triggeredAt: this.offset(-2),
      lastTriggeredAt: this.offset(-1),
      reason: 'SLA_MISSED'
    };
    this.alerts.set(alert.id, alert);
  }

  private buildGroup(
    label: string,
    groupHash: string,
    status: 'green' | 'amber' | 'red',
    inFlight: number,
    late: number,
    failed: number,
    graph: WorkflowGraph
  ): WallboardGroupTile {
    return {
      label,
      groupHash,
      status,
      inFlight,
      late,
      failed,
      countdowns: graph.edges.map((edge) => ({
        label: `${edge.from} → ${edge.to}`,
        dueAt: this.offset((edge.maxLatencySec ?? 300) / 60),
        remainingSec: edge.maxLatencySec ?? 300,
        severity: edge.severity ?? 'amber'
      }))
    };
  }

  private offset(minutesFromNow: number): string {
    const now = new Date();
    now.setMinutes(now.getMinutes() + minutesFromNow);
    return now.toISOString();
  }
}
