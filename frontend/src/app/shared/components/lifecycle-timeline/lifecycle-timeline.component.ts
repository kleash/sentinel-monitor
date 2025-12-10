import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { ItemTimeline } from '../../../core/models';
import { ThemeService } from '../../../core/services/theme.service';
import { StatusPillComponent } from '../status-pill/status-pill.component';
import { CountdownBadgeComponent } from '../countdown-badge/countdown-badge.component';

@Component({
  selector: 'app-lifecycle-timeline',
  standalone: true,
  imports: [NgFor, NgIf, DatePipe, StatusPillComponent, CountdownBadgeComponent],
  template: `
    <section class="timeline" *ngIf="timeline">
      <header>
        <div>
          <div class="label">Correlation ID</div>
          <div class="value">{{ timeline.correlationKey }}</div>
          <div class="sub">Workflow: {{ timeline.workflowName || timeline.workflowId }}</div>
        </div>
        <app-status-pill
          [label]="timeline.currentStage || timeline.status"
          [severity]="timeline.status"
          [meta]="timeline.groupLabel"
        ></app-status-pill>
      </header>
      <div class="summary">
        <div>
          <div class="summary-label">Current Stage</div>
          <div class="summary-value">{{ timeline.currentStage || '—' }}</div>
        </div>
        <div>
          <div class="summary-label">Group</div>
          <div class="summary-value">{{ timeline.groupLabel || 'default' }}</div>
        </div>
        <div>
          <div class="summary-label">Started</div>
          <div class="summary-value">{{ timeline.startedAt | date: 'short': timezone() }}</div>
        </div>
        <div>
          <div class="summary-label">Updated</div>
          <div class="summary-value">{{ timeline.updatedAt | date: 'short': timezone() }}</div>
        </div>
      </div>

      <section class="events" *ngIf="timeline.events?.length">
        <div class="events-head">Lifecycle</div>
        <div class="event-row head">
          <span>Stage</span>
          <span>Event Time</span>
          <span>Received</span>
          <span>Duration</span>
          <span>Flags</span>
        </div>
        <div
          class="event-row"
          *ngFor="let event of timeline.events"
          [class.active]="event.node === timeline.currentStage"
        >
          <span class="node">{{ event.node }}</span>
          <span>{{ event.eventTime | date: 'short': timezone() }}</span>
          <span>{{ event.receivedAt | date: 'shortTime': timezone() }}</span>
          <span>{{ formatDuration(event.durationMs) }}</span>
          <span class="flags">
            <span *ngIf="event.late" class="late pill">Late</span>
            <span *ngIf="event.orderViolation" class="late pill">Order</span>
          </span>
        </div>
      </section>

      <section class="expectations" *ngIf="timeline.pendingExpectations?.length">
        <h4>Pending Expectations</h4>
        <div class="expectation" *ngFor="let exp of timeline.pendingExpectations">
          <div class="path">{{ exp.from }} → {{ exp.to }}</div>
          <app-countdown-badge [label]="'Due'" [dueAt]="exp.dueAt" [severity]="exp.severity" />
        </div>
      </section>
      <section class="alerts" *ngIf="timeline.alerts?.length">
        <h4>Alerts</h4>
        <div class="alert" *ngFor="let alert of timeline.alerts">
          <app-status-pill [label]="alert.title" [severity]="alert.severity"></app-status-pill>
          <span class="reason">{{ alert.reason }}</span>
        </div>
      </section>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .timeline {
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 1rem;
        background: rgba(255, 255, 255, 0.02);
        box-shadow: 0 12px 20px rgba(0, 0, 0, 0.25);
      }
      header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
      }
      .label {
        font-size: 0.75rem;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        opacity: 0.8;
      }
      .value {
        font-size: 1.2rem;
        font-weight: 700;
      }
      .sub {
        color: var(--text-weak);
        font-size: 0.9rem;
      }
      .summary {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 0.5rem;
        margin-bottom: 0.75rem;
      }
      .summary-label {
        font-size: 0.8rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--text-weak);
      }
      .summary-value {
        font-weight: 700;
      }
      .events {
        margin-top: 0.5rem;
        border: 1px solid var(--border-strong);
        border-radius: 0.75rem;
        overflow: hidden;
      }
      .events-head {
        padding: 0.5rem 0.6rem;
        font-weight: 700;
        background: rgba(255, 255, 255, 0.03);
      }
      .event-row {
        display: grid;
        grid-template-columns: 1.2fr repeat(3, minmax(0, 1fr)) minmax(0, 1fr);
        padding: 0.45rem 0.6rem;
        gap: 0.35rem;
        align-items: center;
      }
      .event-row:nth-child(odd) {
        background: rgba(255, 255, 255, 0.02);
      }
      .event-row.head {
        text-transform: uppercase;
        letter-spacing: 0.05em;
        font-size: 0.85rem;
        background: rgba(255, 193, 7, 0.08);
      }
      .event-row.active {
        border-left: 3px solid var(--amber-strong);
        background: rgba(255, 193, 7, 0.06);
      }
      .flags {
        display: flex;
        gap: 0.25rem;
      }
      .pill {
        padding: 0.15rem 0.4rem;
        border: 1px solid var(--amber-strong);
        border-radius: 999px;
        font-size: 0.8rem;
      }
      .late {
        color: var(--red-strong);
      }
      .expectations,
      .alerts {
        margin-top: 0.75rem;
      }
      .expectation,
      .alert {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.4rem 0;
      }
      .path {
        font-weight: 700;
      }
      .reason {
        font-size: 0.9rem;
        color: var(--text-weak);
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LifecycleTimelineComponent {
  @Input() timeline?: ItemTimeline;

  protected readonly timezone: ReturnType<ThemeService['timezone']>;

  constructor(private readonly theme: ThemeService) {
    this.timezone = this.theme.timezone();
  }

  formatDuration(durationMs?: number) {
    if (!durationMs || durationMs <= 0) {
      return '—';
    }
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    if (minutes >= 60) {
      const hours = Math.floor(minutes / 60);
      const mins = minutes % 60;
      return `${hours}h ${mins}m`;
    }
    if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    }
    return `${seconds}s`;
  }
}
