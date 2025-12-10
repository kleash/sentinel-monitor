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
import { Alert, DateFilterState, StageAggregate, WorkflowInstance, WorkflowSummary } from '../../core/models';
import { GraphCanvasComponent } from '../../shared/components/graph-canvas/graph-canvas.component';
import { GroupSelectorComponent } from '../../shared/components/group-selector/group-selector.component';
import { StageTileComponent } from '../../shared/components/stage-tile/stage-tile.component';
import { AlertStripComponent } from '../../shared/components/alert-strip/alert-strip.component';
import { LiveUpdateService } from '../../core/services/live-update.service';
import { DateFilterComponent } from '../../shared/components/date-filter/date-filter.component';
import { DateFilterService } from '../../core/services/date-filter.service';
import { CorrelationDrawerComponent } from '../../shared/components/correlation-drawer/correlation-drawer.component';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-workflow-page',
  standalone: true,
  imports: [
    NgFor,
    NgIf,
    GraphCanvasComponent,
    GroupSelectorComponent,
    StageTileComponent,
    AlertStripComponent,
    DateFilterComponent,
    CorrelationDrawerComponent
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
        <div class="actions">
          <app-date-filter [selection]="dateSelection()" (selectionChange)="onDateChange($event)"></app-date-filter>
          <button type="button" (click)="refreshAggregates()">Refresh</button>
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
          (selectNode)="openStageCorrelations($event)"
        ></app-graph-canvas>
        <div class="tiles">
          <app-stage-tile
            *ngFor="let node of workflow!.graph?.nodes ?? []"
            [nodeKey]="node.key"
            [status]="statusMap()[node.key] || 'green'"
            [aggregate]="aggregateFor(node.key)"
            (click)="openStageCorrelations(node.key)"
          ></app-stage-tile>
        </div>
      </div>
    </section>

    <app-correlation-drawer
      *ngIf="correlationVisible()"
      [heading]="workflow?.name ?? ''"
      [subtitle]="workflow?.key ?? ''"
      [groupLabel]="correlationGroupLabel()"
      [dateLabel]="dateLabel()"
      [items]="correlationItems()"
      [hasMore]="correlationHasMore()"
      [loading]="correlationLoading()"
      [stageOptions]="correlationStageOptions()"
      [activeStage]="correlationStage()"
      [timezone]="timezone()"
      (close)="closeCorrelations()"
      (refresh)="refreshCorrelations()"
      (loadMore)="loadMoreCorrelations()"
      (stageChange)="changeCorrelationStage($event)"
      (view)="viewInstance($event)"
    ></app-correlation-drawer>
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
        gap: 1rem;
      }
      .name {
        font-size: 1.5rem;
        font-weight: 800;
      }
      .key {
        opacity: 0.8;
      }
      .actions {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
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
      .tiles app-stage-tile {
        cursor: pointer;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.4rem 0.7rem;
        border-radius: 0.4rem;
        cursor: pointer;
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
  protected readonly timezone: ReturnType<ThemeService['timezone']>;
  protected correlationVisible = signal(false);
  protected correlationItems = signal<WorkflowInstance[]>([]);
  protected correlationHasMore = signal(false);
  protected correlationLoading = signal(false);
  protected correlationStage = signal<string | undefined>(undefined);
  protected correlationStageOptions = computed(() => this.workflow?.graph?.nodes?.map((n) => ({ key: n.key, label: n.eventType })) ?? []);
  protected correlationGroupLabel = computed(() => {
    const active = this.activeGroupSignal();
    if (!active) {
      return 'All groups';
    }
    return this.groupTiles().find((g) => g.groupHash === active)?.label ?? active;
  });
  private correlationPage = 0;

  groupTiles = computed(() => {
    const wallboard = this.live.wallboard()();
    return wallboard?.workflows.find((wf) => wf.workflowKey === this.workflow?.key)?.groups ?? [];
  });
  activeGroup = this.activeGroupSignal.asReadonly();

  protected get dateSelection() {
    return this.dateFilter.selection;
  }

  protected get dateLabel() {
    return this.dateFilter.label;
  }

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly api: PlatformApiService,
    private readonly live: LiveUpdateService,
    private readonly cdr: ChangeDetectorRef,
    private readonly dateFilter: DateFilterService,
    private readonly theme: ThemeService
  ) {
    this.timezone = this.theme.timezone();
  }

  ngOnInit(): void {
    this.live.setWallboardParams(this.dateFilter.queryParams());
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

  onDateChange(selection: DateFilterState) {
    this.dateFilter.setMode(selection.mode, selection.day);
    this.live.setWallboardParams(this.dateFilter.queryParams());
    if (this.workflow?.id) {
      this.loadAggregates(this.workflow.id, this.activeGroupSignal());
    }
    if (this.correlationVisible()) {
      this.fetchCorrelations(0, false);
    }
  }

  refreshAggregates() {
    if (this.workflow?.id) {
      this.loadAggregates(this.workflow.id, this.activeGroupSignal());
    }
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

  openStageCorrelations(stageKey: string) {
    if (!this.workflow) {
      return;
    }
    this.correlationVisible.set(true);
    this.correlationStage.set(stageKey);
    this.correlationItems.set([]);
    this.correlationHasMore.set(false);
    this.correlationPage = 0;
    this.fetchCorrelations(0, false);
  }

  closeCorrelations() {
    this.correlationVisible.set(false);
    this.correlationStage.set(undefined);
    this.correlationItems.set([]);
    this.correlationHasMore.set(false);
  }

  refreshCorrelations() {
    this.fetchCorrelations(0, false);
  }

  loadMoreCorrelations() {
    this.fetchCorrelations(this.correlationPage + 1, true);
  }

  changeCorrelationStage(stage?: string) {
    this.correlationStage.set(stage || undefined);
    this.fetchCorrelations(0, false);
  }

  viewInstance(instance: WorkflowInstance) {
    this.router.navigate(['/item', instance.correlationId], {
      queryParams: { workflowVersionId: instance.workflowVersionId }
    });
  }

  private loadWorkflow(key: string) {
    this.api.getWorkflow(key).subscribe((wf) => {
      this.workflow = wf;
      this.loadAggregates(wf.id, this.activeGroupSignal());
      const currentAlerts = this.live.alerts()() ?? [];
      const nameMatch = wf.name?.toLowerCase() ?? '';
      this.alerts = currentAlerts.filter((a) => {
        const title = (a.title ?? '').toLowerCase();
        return a.correlationKey === key || (!!nameMatch && title.includes(nameMatch));
      });
      this.cdr.markForCheck();
    });
  }

  private loadAggregates(id: string, groupHash?: string) {
    this.api.getAggregates(id, groupHash, 50, this.dateFilter.queryParams()).subscribe((rows) => {
      this.aggregates = rows;
      this.cdr.markForCheck();
    });
  }

  private fetchCorrelations(page: number, append: boolean) {
    if (!this.workflow?.key) {
      return;
    }
    this.correlationLoading.set(true);
    const params = {
      ...this.dateFilter.queryParams(),
      groupHash: this.activeGroupSignal(),
      stage: this.correlationStage(),
      page,
      size: 25
    };
    this.api.getCorrelations(this.workflow.key, params).subscribe({
      next: (resp) => {
        this.correlationPage = page;
        const current = append ? this.correlationItems() : [];
        this.correlationItems.set([...current, ...resp.items]);
        this.correlationHasMore.set(resp.hasMore);
        this.correlationLoading.set(false);
        this.cdr.markForCheck();
      },
      error: () => {
        this.correlationLoading.set(false);
        this.cdr.markForCheck();
      }
    });
  }
}
