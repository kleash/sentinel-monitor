import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { Alert } from '../../../core/models';
import { StatusPillComponent } from '../status-pill/status-pill.component';

@Component({
  selector: 'app-alert-strip',
  standalone: true,
  imports: [NgFor, NgIf, StatusPillComponent],
  template: `
    <section class="strip" *ngIf="alerts?.length">
      <div class="title">Active Alerts</div>
      <div class="alerts">
        <article *ngFor="let alert of alerts" class="alert" [class.red]="alert.severity === 'red'">
          <div class="headline">
            <app-status-pill [label]="alert.nodeKey" [severity]="alert.severity"></app-status-pill>
            <span class="summary">{{ alert.title }}</span>
            <span class="correlation" *ngIf="alert.correlationKey">/ {{ alert.correlationKey }}</span>
          </div>
          <div class="meta">
            <span>{{ alert.reason ?? 'SLA breach' }}</span>
            <a *ngIf="alert.runbookUrl" [href]="alert.runbookUrl" target="_blank" rel="noreferrer">Runbook</a>
          </div>
          <div class="actions">
            <button type="button" (click)="ack.emit(alert.id)">Ack</button>
            <button type="button" (click)="suppress.emit(alert.id)">Suppress</button>
            <button type="button" (click)="resolve.emit(alert.id)">Resolve</button>
          </div>
        </article>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .strip {
        background: linear-gradient(90deg, rgba(0, 0, 0, 0.6), rgba(36, 36, 36, 0.9));
        border: 1px solid var(--border-strong);
        border-radius: 0.75rem;
        padding: 0.75rem 1rem;
        box-shadow: 0 12px 22px rgba(0, 0, 0, 0.35);
      }
      .title {
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        margin-bottom: 0.5rem;
      }
      .alerts {
        display: grid;
        gap: 0.5rem;
      }
      .alert {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--border-strong);
        border-radius: 0.6rem;
        background: rgba(255, 255, 255, 0.02);
      }
      .alert.red {
        border-color: var(--red-strong);
      }
      .headline {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-weight: 700;
      }
      .summary {
        color: var(--text-strong);
      }
      .correlation {
        font-size: 0.85rem;
        color: var(--text-weak);
      }
      .meta {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        font-size: 0.85rem;
        margin-top: 0.15rem;
      }
      .meta a {
        color: var(--link-strong);
      }
      .actions {
        display: flex;
        gap: 0.5rem;
        margin-top: 0.4rem;
      }
      button {
        background: transparent;
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.3rem 0.5rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
      button:hover {
        border-color: var(--amber-strong);
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertStripComponent {
  @Input() alerts: Alert[] = [];
  @Output() ack = new EventEmitter<string>();
  @Output() suppress = new EventEmitter<string>();
  @Output() resolve = new EventEmitter<string>();
}
