import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { WorkflowInstance } from '../../../core/models';
import { StatusPillComponent } from '../status-pill/status-pill.component';

interface StageOption {
  key: string;
  label: string;
}

@Component({
  selector: 'app-correlation-drawer',
  standalone: true,
  imports: [NgIf, NgFor, DatePipe, StatusPillComponent],
  template: `
    <div class="backdrop" (click)="close.emit()"></div>
    <section class="drawer">
      <header>
        <div>
          <div class="eyebrow">Correlation IDs</div>
          <div class="title">{{ heading }}</div>
          <div class="subtitle">{{ subtitle }}</div>
        </div>
        <div class="actions">
          <span class="date-label">Date: {{ dateLabel }}</span>
          <button type="button" (click)="refresh.emit()">Refresh</button>
          <button type="button" (click)="close.emit()">Close</button>
        </div>
      </header>

      <div class="filters" *ngIf="stageOptions?.length">
        <label for="stage-filter">Stage</label>
        <select id="stage-filter" [value]="activeStage ?? ''" (change)="onStageChange($event)">
          <option value="">Any stage</option>
          <option *ngFor="let opt of stageOptions" [value]="opt.key">{{ opt.label }}</option>
        </select>
      </div>

      <div class="list" *ngIf="items?.length; else empty">
        <div class="row header">
          <span>Correlation ID</span>
          <span>Stage</span>
          <span>Status</span>
          <span>Updated</span>
          <span>Started</span>
          <span>Flags</span>
          <span></span>
        </div>
        <div class="row" *ngFor="let item of items">
          <span class="mono">{{ item.correlationId }}</span>
          <span>{{ item.currentStage || '—' }}</span>
          <span><app-status-pill [label]="item.status.toUpperCase()" [severity]="item.status"></app-status-pill></span>
          <span>{{ item.updatedAt | date: 'short': timezone }}</span>
          <span>{{ item.startedAt | date: 'short': timezone }}</span>
          <span class="flags">
            <span *ngIf="item.late" class="pill late">Late</span>
            <span *ngIf="item.orderViolation" class="pill amber">Order</span>
          </span>
          <span class="cta">
            <button type="button" (click)="view.emit(item)">View lifecycle</button>
          </span>
        </div>
      </div>
      <ng-template #empty>
        <div class="empty">No correlation IDs for this slice.</div>
      </ng-template>

      <footer>
        <div class="summary">
          <span>{{ items?.length || 0 }} showing</span>
          <span *ngIf="groupLabel">Group: {{ groupLabel }}</span>
        </div>
        <button type="button" *ngIf="hasMore" (click)="loadMore.emit()">Show more</button>
      </footer>
      <div class="loading" *ngIf="loading">Loading…</div>
    </section>
  `,
  styles: [
    `
      :host {
        position: fixed;
        inset: 0;
        pointer-events: none;
      }
      .backdrop {
        position: absolute;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        pointer-events: auto;
      }
      .drawer {
        position: absolute;
        right: 0;
        top: 0;
        bottom: 0;
        width: min(620px, 100%);
        background: linear-gradient(175deg, rgba(12, 16, 28, 0.95), rgba(8, 9, 18, 0.98));
        border-left: 1px solid var(--border-strong);
        box-shadow: -12px 0 32px rgba(0, 0, 0, 0.4);
        padding: 1rem;
        overflow: auto;
        pointer-events: auto;
      }
      header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 0.5rem;
        margin-bottom: 0.5rem;
      }
      .eyebrow {
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-size: 0.75rem;
        opacity: 0.75;
      }
      .title {
        font-size: 1.3rem;
        font-weight: 800;
      }
      .subtitle {
        color: var(--text-weak);
      }
      .actions {
        display: flex;
        align-items: center;
        gap: 0.35rem;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.35rem 0.6rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
      .filters {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        margin-bottom: 0.5rem;
      }
      select {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.35rem 0.6rem;
        border-radius: 0.4rem;
      }
      .list {
        border: 1px solid var(--border-strong);
        border-radius: 0.75rem;
        overflow: hidden;
      }
      .row {
        display: grid;
        grid-template-columns: repeat(7, minmax(0, 1fr));
        padding: 0.45rem 0.6rem;
        gap: 0.3rem;
        align-items: center;
      }
      .row:nth-child(odd) {
        background: rgba(255, 255, 255, 0.02);
      }
      .row.header {
        font-size: 0.85rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        background: rgba(255, 193, 7, 0.08);
        border-bottom: 1px solid var(--border-strong);
      }
      .mono {
        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
        font-size: 0.95rem;
      }
      .flags {
        display: flex;
        gap: 0.25rem;
        flex-wrap: wrap;
      }
      .pill {
        padding: 0.2rem 0.45rem;
        border-radius: 999px;
        font-size: 0.8rem;
        border: 1px solid var(--amber-strong);
      }
      .pill.late {
        border-color: var(--red-strong);
        color: var(--red-strong);
      }
      .pill.amber {
        border-color: var(--amber-strong);
        color: var(--amber-strong);
      }
      .cta {
        display: flex;
        justify-content: flex-end;
      }
      footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-top: 0.5rem;
      }
      .summary {
        display: flex;
        gap: 0.6rem;
        color: var(--text-weak);
      }
      .loading {
        margin-top: 0.5rem;
        color: var(--text-weak);
      }
      .empty {
        padding: 0.75rem;
        border: 1px dashed var(--border-strong);
        border-radius: 0.6rem;
        text-align: center;
        color: var(--text-weak);
      }
      .date-label {
        font-size: 0.85rem;
        color: var(--text-weak);
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CorrelationDrawerComponent {
  @Input() heading = '';
  @Input() subtitle = '';
  @Input() dateLabel = '';
  @Input() groupLabel?: string;
  @Input() items: WorkflowInstance[] = [];
  @Input() hasMore = false;
  @Input() loading = false;
  @Input() stageOptions: StageOption[] = [];
  @Input() activeStage?: string;
  @Input() timezone: string | undefined;

  @Output() close = new EventEmitter<void>();
  @Output() loadMore = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();
  @Output() stageChange = new EventEmitter<string | undefined>();
  @Output() view = new EventEmitter<WorkflowInstance>();

  onStageChange(event: Event) {
    const value = (event.target as HTMLSelectElement).value;
    this.stageChange.emit(value || undefined);
  }
}
