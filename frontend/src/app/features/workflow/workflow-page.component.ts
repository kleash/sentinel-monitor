import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  computed,
  signal
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgFor, NgIf } from '@angular/common';
import { Subscription } from 'rxjs';
import { PlatformApiService } from '../../core/services/platform-api.service';
import { Alert, StageAggregate, WorkflowSummary } from '../../core/models';
import { GraphCanvasComponent } from '../../shared/components/graph-canvas/graph-canvas.component';
import { GroupSelectorComponent } from '../../shared/components/group-selector/group-selector.component';
import { StageTileComponent } from '../../shared/components/stage-tile/stage-tile.component';
import { AlertStripComponent } from '../../shared/components/alert-strip/alert-strip.component';
import { LiveUpdateService } from '../../core/services/live-update.service';

@Component({
  selector: 'app-workflow-page',
  standalone: true,
  imports: [
    NgFor,
    NgIf,
    GraphCanvasComponent,
    GroupSelectorComponent,
    StageTileComponent,
    AlertStripComponent
  ],
  template: `
    <section *ngIf="workflow">
      <app-alert-strip
        [alerts]="alerts"
        (ack)="ack($event)"
        (suppress)="suppress($event)"
        (resolve)="resolve($event)"
      ></app-alert-strip>

      <header class="workflow-header">
        <div>
          <div class="name">{{ workflow!.name }}</div>
          <div class="key">{{ workflow!.key }}</div>
        </div>
      </header>

      <app-group-selector
        [groups]="groupTiles()"
        [active]="activeGroup()"
        (select)="selectGroup($event)"
      ></app-group-selector>

      <div class="layout">
        <app-graph-canvas
          *ngIf="workflow?.graph"
          [graph]="workflow!.graph!"
          [statusByNode]="statusMap()"
        ></app-graph-canvas>
        <div class="tiles">
          <app-stage-tile
            *ngFor="let node of workflow!.graph?.nodes ?? []"
            [nodeKey]="node.key"
            [status]="statusMap()[node.key] || 'green'"
            [aggregate]="aggregateFor(node.key)"
          ></app-stage-tile>
        </div>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .workflow-header {
        margin: 0.75rem 0;
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .name {
        font-size: 1.5rem;
        font-weight: 800;
      }
      .key {
        opacity: 0.8;
      }
      .layout {
        display: grid;
        grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
        gap: 1rem;
        margin-top: 0.8rem;
      }
      .tiles {
        display: grid;
        gap: 0.75rem;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkflowPageComponent implements OnInit, OnDestroy {
  workflow?: WorkflowSummary;
  aggregates: StageAggregate[] = [];
  alerts: Alert[] = [];
  private readonly subs: Subscription[] = [];
  private readonly activeGroupSignal = signal<string | undefined>(undefined);

  groupTiles = computed(() => {
    const wallboard = this.live.wallboard()();
    return wallboard?.workflows.find((wf) => wf.workflowKey === this.workflow?.key)?.groups ?? [];
  });
  activeGroup = this.activeGroupSignal.asReadonly();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly api: PlatformApiService,
    private readonly live: LiveUpdateService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.live.start();
    this.subs.push(
      this.route.paramMap.subscribe((params) => {
        const key = params.get('key');
        if (key) {
          this.loadWorkflow(key);
        }
      }),
      this.route.queryParamMap.subscribe((params) => {
        const group = params.get('group') ?? undefined;
        this.activeGroupSignal.set(group);
        if (this.workflow?.id) {
          this.loadAggregates(this.workflow.id, group);
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach((sub) => sub.unsubscribe());
  }

  statusMap() {
    const map: Record<string, 'green' | 'amber' | 'red'> = {};
    this.aggregates.forEach((agg) => {
      if ((agg.failed ?? 0) > 0) {
        map[agg.nodeKey] = 'red';
      } else if ((agg.late ?? 0) > 0) {
        map[agg.nodeKey] = 'amber';
      } else {
        map[agg.nodeKey] = 'green';
      }
    });
    return map;
  }

  aggregateFor(nodeKey: string) {
    return this.aggregates.find((agg) => agg.nodeKey === nodeKey);
  }

  selectGroup(groupHash: string) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { group: groupHash },
      queryParamsHandling: 'merge'
    });
  }

  ack(id: string) {
    this.api.ackAlert(id).subscribe(() => this.live.refreshAlerts());
  }

  suppress(id: string) {
    this.api.suppressAlert(id).subscribe(() => this.live.refreshAlerts());
  }

  resolve(id: string) {
    this.api.resolveAlert(id).subscribe(() => this.live.refreshAlerts());
  }

  private loadWorkflow(key: string) {
    this.api.getWorkflow(key).subscribe((wf) => {
      this.workflow = wf;
      this.loadAggregates(wf.id, this.activeGroupSignal());
      const currentAlerts = this.live.alerts()();
      this.alerts = currentAlerts.filter(
        (a) => a.correlationKey === key || a.title.toLowerCase().includes(wf.name.toLowerCase())
      );
      this.cdr.markForCheck();
    });
  }

  private loadAggregates(id: string, groupHash?: string) {
    this.api.getAggregates(id, groupHash).subscribe((rows) => {
      this.aggregates = rows;
      this.cdr.markForCheck();
    });
  }
}
