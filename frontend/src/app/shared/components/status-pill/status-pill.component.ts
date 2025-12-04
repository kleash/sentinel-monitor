import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { NgClass, NgIf } from '@angular/common';
import { Severity } from '../../../core/models';

@Component({
  selector: 'app-status-pill',
  standalone: true,
  imports: [NgClass, NgIf],
  template: `
    <span class="pill" [ngClass]="severity">
      <span class="dot" [ngClass]="severity"></span>
      <span class="label">{{ label }}</span>
      <span class="meta" *ngIf="meta">{{ meta }}</span>
    </span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }
      .pill {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.3rem 0.75rem;
        border-radius: 999px;
        font-size: 0.85rem;
        letter-spacing: 0.02em;
        border: 1px solid var(--border-strong);
        background: rgba(255, 255, 255, 0.04);
        color: var(--text-strong);
      }
      .pill.red {
        border-color: var(--red-strong);
        color: var(--red-strong);
        background: linear-gradient(120deg, rgba(255, 68, 68, 0.12), rgba(0, 0, 0, 0));
      }
      .pill.amber {
        border-color: var(--amber-strong);
        color: var(--amber-strong);
        background: linear-gradient(120deg, rgba(255, 193, 7, 0.12), rgba(0, 0, 0, 0));
      }
      .pill.green {
        border-color: var(--green-strong);
        color: var(--green-strong);
        background: linear-gradient(120deg, rgba(46, 204, 113, 0.12), rgba(0, 0, 0, 0));
      }
      .dot {
        width: 0.65rem;
        height: 0.65rem;
        border-radius: 999px;
        display: inline-flex;
        background: var(--text-weak);
        box-shadow: 0 0 6px rgba(0, 0, 0, 0.2);
        animation: pulse 3s ease-in-out infinite;
      }
      .dot.red {
        background: var(--red-strong);
        animation-duration: 1.4s;
      }
      .dot.amber {
        background: var(--amber-strong);
      }
      .dot.green {
        background: var(--green-strong);
        animation-duration: 3.4s;
      }
      .meta {
        font-size: 0.75rem;
        opacity: 0.8;
      }
      @keyframes pulse {
        0% {
          transform: scale(0.9);
          opacity: 0.8;
        }
        50% {
          transform: scale(1.1);
          opacity: 1;
        }
        100% {
          transform: scale(0.9);
          opacity: 0.8;
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusPillComponent {
  @Input({ required: true }) label!: string;
  @Input() severity: Severity = 'green';
  @Input() meta?: string;
}
