import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { ItemTimeline } from '../../../core/models';
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
          <div class="label">Correlation</div>
          <div class="value">{{ timeline.correlationKey }}</div>
        </div>
        <app-status-pill [label]="timeline.workflowId" [severity]="timeline.status"></app-status-pill>
      </header>
      <ol>
        <li *ngFor="let event of timeline.events">
          <div class="event-head">
            <span class="node">{{ event.node }}</span>
            <span class="time">{{ event.eventTime | date: 'medium' }}</span>
          </div>
          <div class="event-meta">
            <span>received {{ event.receivedAt | date: 'mediumTime' }}</span>
            <span *ngIf="event.late" class="late">Late</span>
            <span *ngIf="event.orderViolation" class="late">Order violation</span>
          </div>
        </li>
      </ol>
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
      ol {
        list-style: none;
        padding: 0;
        margin: 0;
        display: grid;
        gap: 0.6rem;
      }
      li {
        border-left: 2px solid var(--border-strong);
        padding-left: 0.75rem;
      }
      .event-head {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-weight: 700;
      }
      .event-meta {
        font-size: 0.85rem;
        color: var(--text-weak);
        display: flex;
        gap: 1rem;
      }
      .late {
        color: var(--red-strong);
        font-weight: 700;
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
}
