import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgIf } from '@angular/common';
import { Subscription } from 'rxjs';
import { PlatformApiService } from '../../core/services/platform-api.service';
import { ItemTimeline } from '../../core/models';
import { LifecycleTimelineComponent } from '../../shared/components/lifecycle-timeline/lifecycle-timeline.component';

@Component({
  selector: 'app-item-page',
  standalone: true,
  imports: [NgIf, LifecycleTimelineComponent],
  template: `
    <section *ngIf="timeline">
      <header class="header">
        <div>
          <div class="label">Item</div>
          <div class="value">{{ timeline!.correlationKey }}</div>
        </div>
        <button type="button" (click)="refresh()">Refresh</button>
      </header>
      <app-lifecycle-timeline [timeline]="timeline"></app-lifecycle-timeline>
    </section>
    <section *ngIf="!timeline" class="empty">No item selected</section>
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
        margin-bottom: 0.5rem;
      }
      .label {
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-size: 0.85rem;
      }
      .value {
        font-size: 1.3rem;
        font-weight: 800;
      }
      .empty {
        text-align: center;
        padding: 2rem;
        border: 1px dashed var(--border-strong);
        border-radius: 0.75rem;
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
export class ItemPageComponent implements OnInit, OnDestroy {
  timeline?: ItemTimeline;
  private readonly subs: Subscription[] = [];
  private correlationKey?: string;
  private workflowVersionId?: string;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: PlatformApiService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subs.push(
      this.route.paramMap.subscribe((params) => {
        this.correlationKey = params.get('correlationKey') ?? undefined;
        this.refresh();
      }),
      this.route.queryParamMap.subscribe((params) => {
        this.workflowVersionId = params.get('workflowVersionId') ?? undefined;
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach((sub) => sub.unsubscribe());
  }

  refresh() {
    if (!this.correlationKey) {
      return;
    }
    this.api
      .getItem(this.correlationKey, this.workflowVersionId)
      .subscribe((timeline) => {
        this.timeline = timeline;
        this.cdr.markForCheck();
      });
  }
}
