import { ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { NgClass } from '@angular/common';
import { Severity } from '../../../core/models';

@Component({
  selector: 'app-countdown-badge',
  standalone: true,
  imports: [NgClass],
  template: `
    <div class="badge" [ngClass]="severity">
      <div class="label">{{ label }}</div>
      <div class="time">{{ remainingLabel }}</div>
    </div>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .badge {
        padding: 0.25rem 0.6rem;
        border-radius: 0.6rem;
        font-size: 0.8rem;
        display: grid;
        gap: 0.05rem;
        color: var(--text-strong);
        border: 1px solid var(--border-strong);
        background: rgba(255, 255, 255, 0.04);
      }
      .badge.red {
        border-color: var(--red-strong);
        color: var(--red-strong);
      }
      .badge.amber {
        border-color: var(--amber-strong);
        color: var(--amber-strong);
      }
      .badge.green {
        border-color: var(--green-strong);
        color: var(--green-strong);
      }
      .label {
        opacity: 0.85;
        font-size: 0.75rem;
      }
      .time {
        font-weight: 700;
        letter-spacing: 0.03em;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CountdownBadgeComponent implements OnInit, OnDestroy {
  @Input({ required: true }) dueAt!: string;
  @Input({ required: true }) label!: string;
  @Input() severity: Severity = 'green';

  remainingLabel = '';
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.update();
    this.timer = setInterval(() => this.update(), 1000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  private update() {
    const now = new Date();
    const target = new Date(this.dueAt);
    const diff = target.getTime() - now.getTime();
    const late = diff < 0;
    const totalSeconds = Math.abs(Math.round(diff / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    this.remainingLabel = `${late ? '+' : '-'}${minutes}m ${seconds.toString().padStart(2, '0')}s`;
  }
}
