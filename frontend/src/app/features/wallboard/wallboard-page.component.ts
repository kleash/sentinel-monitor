import { ChangeDetectionStrategy, Component, OnInit, computed } from '@angular/core';
import { DatePipe, NgFor, NgIf, SlicePipe } from '@angular/common';
import { Router } from '@angular/router';
import { LiveUpdateService } from '../../core/services/live-update.service';
import { StatusPillComponent } from '../../shared/components/status-pill/status-pill.component';
import { CountdownBadgeComponent } from '../../shared/components/countdown-badge/countdown-badge.component';
import { WallboardWorkflowTile } from '../../core/models';

@Component({
  selector: 'app-wallboard-page',
  standalone: true,
  imports: [NgFor, NgIf, DatePipe, SlicePipe, StatusPillComponent, CountdownBadgeComponent],
  template: `
    <section *ngIf="wallboard() as board" class="wallboard">
      <header>
        <div>
          <div class="label">Updated</div>
          <div class="value">{{ board.updatedAt | date: 'mediumTime' }}</div>
        </div>
        <button type="button" (click)="refresh()">Refresh</button>
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
            </div>
          </section>
        </article>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .wallboard header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
        gap: 1rem;
      }
      .workflow-card {
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 0.75rem;
        background: linear-gradient(160deg, rgba(255, 255, 255, 0.02), rgba(0, 0, 0, 0.25));
        box-shadow: 0 12px 20px rgba(0, 0, 0, 0.25);
        cursor: pointer;
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
export class WallboardPageComponent implements OnInit {
  protected wallboard = computed(() => this.live.wallboard()() ?? { workflows: [], updatedAt: '' });

  constructor(private readonly live: LiveUpdateService, private readonly router: Router) {}

  ngOnInit(): void {
    this.live.start();
  }

  refresh() {
    this.live.refreshWallboard();
  }

  open(workflow: WallboardWorkflowTile, groupHash: string) {
    this.router.navigate(['/workflow', workflow.workflowKey], { queryParams: { group: groupHash } });
  }
}
