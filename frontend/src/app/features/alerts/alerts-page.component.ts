import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { PlatformApiService } from '../../core/services/platform-api.service';
import { Alert } from '../../core/models';
import { StatusPillComponent } from '../../shared/components/status-pill/status-pill.component';

@Component({
  selector: 'app-alerts-page',
  standalone: true,
  imports: [NgFor, NgIf, StatusPillComponent],
  template: `
    <section>
      <header class="header">
        <div>
          <div class="title">Alert Console</div>
          <div class="meta">{{ alerts.length }} total</div>
        </div>
        <button type="button" (click)="load()">Refresh</button>
      </header>
      <div class="list">
        <article *ngFor="let alert of alerts" class="alert">
          <div class="headline">
            <app-status-pill [label]="alert.nodeKey" [severity]="alert.severity"></app-status-pill>
            <div class="title">{{ alert.title }}</div>
            <div class="state">{{ alert.state }}</div>
          </div>
          <div class="meta">
            <span>{{ alert.correlationKey }}</span>
            <span>{{ alert.triggeredAt }}</span>
            <a *ngIf="alert.runbookUrl" [href]="alert.runbookUrl" target="_blank" rel="noreferrer">Runbook</a>
          </div>
          <div class="actions">
            <button type="button" (click)="ack(alert.id)">Ack</button>
            <button type="button" (click)="suppress(alert.id)">Suppress</button>
            <button type="button" (click)="resolve(alert.id)">Resolve</button>
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
      .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
      }
      .title {
        font-weight: 800;
        font-size: 1.2rem;
      }
      .meta {
        font-size: 0.9rem;
        opacity: 0.8;
      }
      .list {
        display: grid;
        gap: 0.6rem;
      }
      .alert {
        border: 1px solid var(--border-strong);
        border-radius: 0.75rem;
        padding: 0.6rem;
        background: rgba(255, 255, 255, 0.02);
      }
      .headline {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .state {
        margin-left: auto;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-size: 0.8rem;
      }
      .meta {
        display: flex;
        gap: 1rem;
        margin-top: 0.35rem;
        color: var(--text-weak);
      }
      .actions {
        display: flex;
        gap: 0.5rem;
        margin-top: 0.5rem;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.35rem 0.7rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertsPageComponent implements OnInit {
  alerts: Alert[] = [];

  constructor(private readonly api: PlatformApiService, private readonly cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.load();
  }

  load() {
    this.api.getAlerts().subscribe((alerts) => {
      this.alerts = alerts;
      this.cdr.markForCheck();
    });
  }

  ack(id: string) {
    this.alerts = this.alerts.map((alert) => (alert.id === id ? { ...alert, state: 'ack' } : alert));
    this.cdr.markForCheck();
    this.api.ackAlert(id).subscribe((updated) => this.applyUpdate(updated));
  }

  suppress(id: string) {
    this.alerts = this.alerts.map((alert) =>
      alert.id === id ? { ...alert, state: 'suppressed' } : alert
    );
    this.cdr.markForCheck();
    this.api.suppressAlert(id).subscribe((updated) => this.applyUpdate(updated));
  }

  resolve(id: string) {
    this.alerts = this.alerts.map((alert) =>
      alert.id === id ? { ...alert, state: 'resolved' } : alert
    );
    this.cdr.markForCheck();
    this.api.resolveAlert(id).subscribe((updated) => this.applyUpdate(updated));
  }

  private applyUpdate(updated: Alert) {
    this.alerts = this.alerts.map((alert) => (alert.id === updated.id ? updated : alert));
    this.cdr.markForCheck();
  }
}
