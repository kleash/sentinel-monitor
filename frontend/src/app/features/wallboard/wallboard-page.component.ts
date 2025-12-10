import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, computed, signal } from '@angular/core';
import { DatePipe, NgFor, NgIf, SlicePipe } from '@angular/common';
import { Router } from '@angular/router';
import { LiveUpdateService } from '../../core/services/live-update.service';
import { ThemeService } from '../../core/services/theme.service';
import { StatusPillComponent } from '../../shared/components/status-pill/status-pill.component';
import { CountdownBadgeComponent } from '../../shared/components/countdown-badge/countdown-badge.component';
import { DateFilterState, WallboardWorkflowTile, WorkflowInstance, WorkflowSummary } from '../../core/models';
import { DateFilterComponent } from '../../shared/components/date-filter/date-filter.component';
import { DateFilterService } from '../../core/services/date-filter.service';
import { PlatformApiService } from '../../core/services/platform-api.service';
import { CorrelationDrawerComponent } from '../../shared/components/correlation-drawer/correlation-drawer.component';

@Component({
  selector: 'app-wallboard-page',
  standalone: true,
  imports: [
    NgFor,
    NgIf,
    DatePipe,
    SlicePipe,
    StatusPillComponent,
    CountdownBadgeComponent,
    DateFilterComponent,
    CorrelationDrawerComponent
  ],
  template: `
    <section *ngIf="wallboard() as board" class="wallboard">
      <header class="page-header">
        <div class="timestamps">
          <div class="label">Updated</div>
          <div class="value">{{ board.updatedAt | date: 'mediumTime': timezone() }}</div>
          <div class="active-date">Viewing: {{ dateLabel() }}</div>
        </div>
        <div class="header-actions">
          <app-date-filter [selection]="dateSelection()" (selectionChange)="onDateChange($event)"></app-date-filter>
          <button type="button" (click)="refresh()">Refresh</button>
        </div>
      </header>
      <div class="grid">
        <article
          *ngFor="let workflow of board.workflows"
          class="workflow-card"
          [class.alert]="workflow.status === 'red'"
        >
          <header>
            <div>
              <div class="name">{{ workflow.name }}</div>
              <div class="key">{{ workflow.workflowKey }}</div>
            </div>
            <app-status-pill [label]="workflow.status.toUpperCase()" [severity]="workflow.status"></app-status-pill>
            <button class="quiet" type="button" (click)="openCorrelations(workflow, undefined, $event)">
              Correlation IDs
            </button>
          </header>
          <section class="groups">
            <div *ngFor="let group of workflow.groups" class="group" (click)="open(workflow, group.groupHash)">
              <div class="group-head">
                <div class="label">{{ group.label }}</div>
                <app-status-pill [label]="group.status" [severity]="group.status"></app-status-pill>
              </div>
              <div class="group-metrics">
                <div>
                  <span class="value">{{ group.inFlight }}</span>
                  <span class="meta">In Flight</span>
                </div>
                <div>
                  <span class="value">{{ group.late }}</span>
                  <span class="meta">Late</span>
                </div>
                <div>
                  <span class="value">{{ group.failed }}</span>
                  <span class="meta">Failed</span>
                </div>
              </div>
              <div class="countdowns">
                <app-countdown-badge
                  *ngFor="let timer of group.countdowns | slice: 0:2"
                  [label]="timer.label"
                  [dueAt]="timer.dueAt"
                  [severity]="timer.severity"
                />
              </div>
              <div class="group-actions">
                <button type="button" (click)="openCorrelations(workflow, group.groupHash, $event)">Correlation IDs</button>
                <button type="button" class="quiet" (click)="open(workflow, group.groupHash); $event.stopPropagation();">
                  Workflow view
                </button>
              </div>
            </div>
          </section>
        </article>
      </div>
    </section>

    <app-correlation-drawer
      *ngIf="correlationWorkflow()"
      [heading]="correlationWorkflow()?.name ?? ''"
      [subtitle]="correlationWorkflow()?.workflowKey ?? ''"
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
      .page-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 0.75rem;
        gap: 1rem;
      }
      .timestamps {
        display: grid;
        gap: 0.1rem;
      }
      .active-date {
        color: var(--text-weak);
        font-size: 0.9rem;
      }
      .header-actions {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
        gap: 1rem;
      }
      :host-context(.wallboard-mode) .grid {
        grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
      }
      .workflow-card {
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 0.75rem;
        background: linear-gradient(160deg, rgba(255, 255, 255, 0.02), rgba(0, 0, 0, 0.25));
        box-shadow: 0 12px 20px rgba(0, 0, 0, 0.25);
        cursor: pointer;
      }
      :host-context(.wallboard-mode) .workflow-card {
        transform: scale(1.01);
        transition: transform 120ms ease, box-shadow 120ms ease;
      }
      :host-context(.wallboard-mode) .workflow-card:hover {
        transform: scale(1.015);
        box-shadow: 0 16px 28px rgba(0, 0, 0, 0.32);
      }
      .workflow-card.alert {
        border-color: var(--red-strong);
        box-shadow: 0 12px 28px rgba(255, 68, 68, 0.15);
      }
      .workflow-card header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.6rem;
      }
      .name {
        font-size: 1.1rem;
        font-weight: 800;
      }
      .key {
        font-size: 0.85rem;
        opacity: 0.8;
      }
      .groups {
        display: grid;
        gap: 0.5rem;
      }
      .group {
        border: 1px dashed var(--border-strong);
        border-radius: 0.75rem;
        padding: 0.6rem;
      }
      .group-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .group-metrics {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        margin-top: 0.4rem;
        gap: 0.4rem;
      }
      .value {
        display: block;
        font-weight: 800;
        font-size: 1.2rem;
      }
      .meta {
        font-size: 0.75rem;
        opacity: 0.8;
      }
      .countdowns {
        display: flex;
        gap: 0.35rem;
        flex-wrap: wrap;
        margin-top: 0.4rem;
      }
      .group-actions {
        display: flex;
        gap: 0.35rem;
        margin-top: 0.5rem;
        flex-wrap: wrap;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.4rem 0.7rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
      button.quiet {
        border-style: dashed;
        opacity: 0.85;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WallboardPageComponent implements OnInit {
  protected wallboard = computed(() => this.live.wallboard()() ?? { workflows: [], updatedAt: '' });
  protected readonly timezone: ReturnType<ThemeService['timezone']>;
  protected correlationWorkflow = signal<WallboardWorkflowTile | null>(null);
  protected correlationGroup = signal<string | undefined>(undefined);
  protected correlationItems = signal<WorkflowInstance[]>([]);
  protected correlationHasMore = signal(false);
  protected correlationLoading = signal(false);
  protected correlationStage = signal<string | undefined>(undefined);
  protected correlationSummary = signal<WorkflowSummary | null>(null);
  protected correlationGroupLabel = computed(() => {
    const wf = this.correlationWorkflow();
    if (!wf) {
      return '';
    }
    const groupHash = this.correlationGroup();
    if (!groupHash) {
      return 'All groups';
    }
    return wf.groups.find((g) => g.groupHash === groupHash)?.label ?? groupHash;
  });
  protected correlationStageOptions = computed(() => {
    const summary = this.correlationSummary();
    const nodes = summary?.graph?.nodes ?? [];
    return nodes.map((node) => ({ key: node.key, label: `${node.key} (${node.eventType})` }));
  });

  private correlationPage = 0;

  protected get dateSelection() {
    return this.dateFilter.selection;
  }

  protected get dateLabel() {
    return this.dateFilter.label;
  }

  constructor(
    private readonly live: LiveUpdateService,
    private readonly router: Router,
    private readonly theme: ThemeService,
    private readonly dateFilter: DateFilterService,
    private readonly api: PlatformApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.timezone = this.theme.timezone();
  }

  ngOnInit(): void {
    this.live.setWallboardParams(this.dateFilter.queryParams());
    this.live.start();
  }

  refresh() {
    this.live.refreshWallboard();
  }

  onDateChange(selection: DateFilterState) {
    this.dateFilter.setMode(selection.mode, selection.day);
    this.live.setWallboardParams(this.dateFilter.queryParams());
    this.refresh();
    if (this.correlationWorkflow()) {
      this.fetchCorrelations(0, false);
    }
  }

  open(workflow: WallboardWorkflowTile, groupHash: string) {
    this.router.navigate(['/workflow', workflow.workflowKey], { queryParams: { group: groupHash } });
  }

  openCorrelations(workflow: WallboardWorkflowTile, groupHash?: string, event?: Event) {
    event?.stopPropagation();
    this.correlationWorkflow.set(workflow);
    this.correlationGroup.set(groupHash);
    this.correlationStage.set(undefined);
    this.correlationItems.set([]);
    this.correlationHasMore.set(false);
    this.correlationPage = 0;
    this.loadWorkflowSummary(workflow.workflowKey);
    this.fetchCorrelations(0, false);
  }

  closeCorrelations() {
    this.correlationWorkflow.set(null);
    this.correlationItems.set([]);
    this.correlationHasMore.set(false);
    this.correlationStage.set(undefined);
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

  private fetchCorrelations(page: number, append: boolean) {
    const wf = this.correlationWorkflow();
    if (!wf) {
      return;
    }
    this.correlationLoading.set(true);
    const params = {
      ...this.dateFilter.queryParams(),
      groupHash: this.correlationGroup(),
      stage: this.correlationStage(),
      page,
      size: 25
    };
    this.api.getCorrelations(wf.workflowKey, params).subscribe({
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

  private loadWorkflowSummary(key: string) {
    this.api.getWorkflow(key).subscribe((wf) => {
      this.correlationSummary.set(wf);
      this.cdr.markForCheck();
    });
  }
}
