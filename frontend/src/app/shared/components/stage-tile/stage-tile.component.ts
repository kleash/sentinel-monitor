import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';
import { StageAggregate, WallboardCountdown, Severity } from '../../../core/models';
import { StatusPillComponent } from '../status-pill/status-pill.component';
import { CountdownBadgeComponent } from '../countdown-badge/countdown-badge.component';

@Component({
  selector: 'app-stage-tile',
  standalone: true,
  imports: [StatusPillComponent, CountdownBadgeComponent, NgIf],
  template: `
    <article class="tile">
      <header>
        <div>
          <div class="title">{{ nodeKey }}</div>
          <app-status-pill [label]="statusLabel" [severity]="status"></app-status-pill>
        </div>
        <app-countdown-badge
          *ngIf="countdown"
          [label]="countdown.label"
          [dueAt]="countdown.dueAt"
          [severity]="countdown.severity"
        />
      </header>
      <dl>
        <div>
          <dt>In Flight</dt>
          <dd>{{ aggregate?.inFlight ?? 0 }}</dd>
        </div>
        <div>
          <dt>Completed</dt>
          <dd>{{ aggregate?.completed ?? 0 }}</dd>
        </div>
        <div class="late" [class.alerting]="(aggregate?.late ?? 0) > 0">
          <dt>Late</dt>
          <dd>{{ aggregate?.late ?? 0 }}</dd>
        </div>
        <div class="failed" [class.alerting]="(aggregate?.failed ?? 0) > 0">
          <dt>Failed</dt>
          <dd>{{ aggregate?.failed ?? 0 }}</dd>
        </div>
      </dl>
      <footer>
        <div class="metric" *ngIf="aggregate?.avgLatencyMs">
          avg latency
          <span>{{ aggregate?.avgLatencyMs }}ms</span>
        </div>
        <div class="metric" *ngIf="aggregate?.p95LatencyMs">
          p95
          <span>{{ aggregate?.p95LatencyMs }}ms</span>
        </div>
      </footer>
    </article>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .tile {
        background: linear-gradient(145deg, rgba(255, 255, 255, 0.03), rgba(0, 0, 0, 0.2));
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 1rem;
        box-shadow: 0 12px 30px rgba(0, 0, 0, 0.25);
        min-height: 180px;
      }
      header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
      }
      .title {
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-weight: 700;
        color: var(--text-strong);
        margin-bottom: 0.2rem;
      }
      dl {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 0.75rem;
        margin: 0;
      }
      dt {
        font-size: 0.75rem;
        opacity: 0.8;
      }
      dd {
        margin: 0.1rem 0 0;
        font-size: 1.4rem;
        font-weight: 700;
      }
      .late.alerting dd,
      .failed.alerting dd {
        color: var(--amber-strong);
      }
      .failed.alerting dd {
        color: var(--red-strong);
      }
      footer {
        display: flex;
        gap: 1rem;
        margin-top: 0.8rem;
        font-size: 0.85rem;
        opacity: 0.9;
      }
      .metric span {
        display: inline-block;
        margin-left: 0.2rem;
        font-weight: 700;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StageTileComponent {
  @Input({ required: true }) nodeKey!: string;
  @Input() aggregate?: StageAggregate;
  @Input() countdown?: WallboardCountdown;
  @Input() status: Severity = 'green';

  get statusLabel(): string {
    if (this.status === 'red') {
      return 'At Risk';
    }
    if (this.status === 'amber') {
      return 'Attention';
    }
    return 'Healthy';
  }
}
