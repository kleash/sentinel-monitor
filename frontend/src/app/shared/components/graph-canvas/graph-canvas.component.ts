import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges
} from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { WorkflowGraph, WorkflowNode, Severity } from '../../../core/models';
import { StatusPillComponent } from '../status-pill/status-pill.component';

interface PositionedNode {
  node: WorkflowNode;
  x: number;
  y: number;
  status: Severity;
}

@Component({
  selector: 'app-graph-canvas',
  standalone: true,
  imports: [NgFor, NgIf, StatusPillComponent],
  template: `
    <div class="graph">
      <svg [attr.viewBox]="'0 0 ' + viewWidth + ' ' + viewHeight" preserveAspectRatio="xMidYMid meet">
        <defs>
          <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto">
            <path d="M0,0 L10,5 L0,10 z" class="edge-marker" />
          </marker>
        </defs>
        <g *ngFor="let edge of graph?.edges" class="edge">
          <line
            [attr.x1]="nodePosition(edge.from)?.x"
            [attr.y1]="nodePosition(edge.from)?.y"
            [attr.x2]="nodePosition(edge.to)?.x"
            [attr.y2]="nodePosition(edge.to)?.y"
            marker-end="url(#arrow)"
            [attr.class]="edge.severity ?? 'green'"
          />
          <text
            *ngIf="edge.maxLatencySec"
            [attr.x]="midpoint(edge.from, edge.to).x"
            [attr.y]="midpoint(edge.from, edge.to).y - 8"
            class="edge-label"
          >
            SLA {{ edge.maxLatencySec }}s
          </text>
          <title>{{ edge.from }} → {{ edge.to }}</title>
        </g>
        <g
          *ngFor="let node of positionedNodes"
          class="node-group"
          (click)="selectNode.emit(node.node.key)"
          tabindex="0"
        >
          <rect
            class="node"
            [attr.class]="node.status"
            [attr.x]="node.x - nodeRadius"
            [attr.y]="node.y - nodeRadius"
            [attr.width]="nodeRadius * 2"
            [attr.height]="nodeRadius * 2"
            rx="14"
            ry="14"
          ></rect>
          <rect
            class="node-glow"
            [attr.class]="node.status"
            [attr.x]="node.x - nodeRadius"
            [attr.y]="node.y - nodeRadius"
            [attr.width]="nodeRadius * 2"
            [attr.height]="nodeRadius * 2"
            rx="18"
            ry="18"
          ></rect>
          <text [attr.x]="node.x" [attr.y]="node.y" class="node-label">{{ node.node.key }}</text>
          <text [attr.x]="node.x" [attr.y]="node.y + 16" class="node-type">{{ node.node.eventType }}</text>
          <text *ngIf="node.node.start" [attr.x]="node.x" [attr.y]="node.y - nodeRadius - 6" class="badge">Start</text>
          <text
            *ngIf="node.node.terminal"
            [attr.x]="node.x"
            [attr.y]="node.y + nodeRadius + 16"
            class="badge terminal"
          >
            Terminal
          </text>
          <title>{{ node.node.eventType }}</title>
        </g>
      </svg>
      <div class="legend" *ngIf="positionedNodes.length">
        <app-status-pill label="Healthy" severity="green"></app-status-pill>
        <app-status-pill label="Attention" severity="amber"></app-status-pill>
        <app-status-pill label="At Risk" severity="red"></app-status-pill>
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .graph {
        background: radial-gradient(circle at 25% 25%, rgba(59, 130, 246, 0.12), transparent 22%),
          radial-gradient(circle at 80% 30%, rgba(255, 193, 7, 0.1), transparent 18%),
          linear-gradient(135deg, rgba(17, 24, 39, 0.95), rgba(6, 8, 15, 0.98));
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 0.5rem;
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 14px 30px rgba(0, 0, 0, 0.35);
        position: relative;
        overflow: hidden;
      }
      svg {
        width: 100%;
        height: 420px;
      }
      .node {
        fill: rgba(255, 255, 255, 0.04);
        stroke: var(--border-strong);
        stroke-width: 1.5;
        filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.4));
      }
      .node.green {
        stroke: var(--green-strong);
        fill: rgba(0, 200, 83, 0.12);
      }
      .node.amber {
        stroke: var(--amber-strong);
        fill: rgba(255, 193, 7, 0.1);
      }
      .node.red {
        stroke: var(--red-strong);
        fill: rgba(255, 82, 82, 0.12);
      }
      .node-glow {
        fill: none;
        stroke-width: 10;
        opacity: 0.12;
      }
      .node-glow.green {
        stroke: var(--green-strong);
      }
      .node-glow.amber {
        stroke: var(--amber-strong);
      }
      .node-glow.red {
        stroke: var(--red-strong);
      }
      .node-label {
        font-size: 0.9rem;
        fill: var(--text-strong);
        font-weight: 700;
        text-anchor: middle;
      }
      .node-type {
        font-size: 0.7rem;
        fill: var(--text-weak);
        text-anchor: middle;
      }
      line {
        stroke: rgba(255, 255, 255, 0.4);
        stroke-width: 2.4;
      }
      line.red {
        stroke: var(--red-strong);
      }
      line.amber {
        stroke: var(--amber-strong);
      }
      line.green {
        stroke: var(--green-strong);
      }
      .edge-marker {
        fill: var(--border-strong);
      }
      .edge-label {
        fill: var(--text-weak);
        font-size: 0.65rem;
        text-anchor: middle;
        background: rgba(0, 0, 0, 0.5);
      }
      .badge {
        font-size: 0.65rem;
        fill: var(--text-weak);
        text-anchor: middle;
        letter-spacing: 0.04em;
      }
      .badge.terminal {
        fill: var(--amber-strong);
      }
      .node-group {
        cursor: pointer;
      }
      .node-group:focus-visible .node {
        stroke-width: 3;
      }
      .legend {
        position: absolute;
        right: 1rem;
        top: 1rem;
        display: flex;
        gap: 0.5rem;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GraphCanvasComponent implements OnChanges {
  @Input({ required: true }) graph!: WorkflowGraph;
  @Input() statusByNode?: Record<string, Severity>;
  @Output() selectNode = new EventEmitter<string>();

  positionedNodes: PositionedNode[] = [];
  viewWidth = 1200;
  viewHeight = 360;
  nodeRadius = 55;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['graph']) {
      this.layout();
    }
  }

  nodePosition(nodeKey: string): PositionedNode | undefined {
    return this.positionedNodes.find((n) => n.node.key === nodeKey);
  }

  midpoint(from: string, to: string) {
    const fromNode = this.nodePosition(from);
    const toNode = this.nodePosition(to);
    return {
      x: ((fromNode?.x ?? 0) + (toNode?.x ?? 0)) / 2,
      y: ((fromNode?.y ?? 0) + (toNode?.y ?? 0)) / 2
    };
  }

  private layout() {
    if (!this.graph?.nodes?.length) {
      this.positionedNodes = [];
      return;
    }
    const levels: Record<string, number> = {};
    const incoming: Record<string, number> = {};
    this.graph.nodes.forEach((node) => (incoming[node.key] = 0));
    this.graph.edges?.forEach((edge) => {
      incoming[edge.to] = (incoming[edge.to] ?? 0) + 1;
    });
    const queue: WorkflowNode[] = this.graph.nodes.filter((n) => n.start || (incoming[n.key] ?? 0) === 0);
    queue.forEach((node) => (levels[node.key] = 0));
    while (queue.length) {
      const node = queue.shift()!;
      const currentLevel = levels[node.key] ?? 0;
      const children = this.graph.edges?.filter((e) => e.from === node.key) ?? [];
      children.forEach((edge) => {
        const nextLevel = currentLevel + 1;
        if (levels[edge.to] === undefined || nextLevel > levels[edge.to]) {
          levels[edge.to] = nextLevel;
          const childNode = this.graph.nodes.find((n) => n.key === edge.to);
          if (childNode) {
            queue.push(childNode);
          }
        }
      });
    }
    const maxLevel = Math.max(...Object.values(levels));
    const columns: Record<number, WorkflowNode[]> = {};
    this.graph.nodes.forEach((node) => {
      const lvl = levels[node.key] ?? 0;
      columns[lvl] = columns[lvl] ?? [];
      columns[lvl].push(node);
    });
    const maxPerColumn = Math.max(...Object.values(columns).map((arr) => arr.length));
    this.viewWidth = Math.max(960, (maxLevel + 1) * 220);
    this.viewHeight = Math.max(340, maxPerColumn * 140);
    const horizontalGap = this.viewWidth / (maxLevel + 2);
    const nodes: PositionedNode[] = [];
    Object.entries(columns).forEach(([levelStr, nodesInLevel]) => {
      const level = Number(levelStr);
      const verticalGap = this.viewHeight / (nodesInLevel.length + 1);
      nodesInLevel.forEach((node, idx) => {
        const status = this.statusByNode?.[node.key] ?? 'green';
        nodes.push({
          node,
          x: horizontalGap * (level + 1),
          y: verticalGap * (idx + 1),
          status
        });
      });
    });
    this.positionedNodes = nodes;
  }
}
