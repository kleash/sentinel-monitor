import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NgIf } from '@angular/common';
import { DateFilterMode, DateFilterState } from '../../../core/models';

@Component({
  selector: 'app-date-filter',
  standalone: true,
  imports: [NgIf],
  template: `
    <div class="date-filter">
      <div class="label">{{ label }}</div>
      <div class="controls">
        <button type="button" [class.active]="selection?.mode === 'today'" (click)="select('today')">Today</button>
        <button type="button" [class.active]="selection?.mode === 'day'" (click)="select('day')">
          Specific date
        </button>
        <button type="button" [class.active]="selection?.mode === 'all'" (click)="select('all')">All days</button>
      </div>
      <div class="picker" *ngIf="selection?.mode === 'day'">
        <input type="date" [value]="selection?.day ?? today" (change)="onDayChange($event)" />
      </div>
      <div class="active-label">Active: {{ activeLabel }}</div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .date-filter {
        display: grid;
        gap: 0.25rem;
      }
      .label {
        text-transform: uppercase;
        letter-spacing: 0.08em;
        font-size: 0.75rem;
        opacity: 0.8;
      }
      .controls {
        display: flex;
        gap: 0.35rem;
        flex-wrap: wrap;
      }
      button {
        border: 1px solid var(--border-strong);
        background: rgba(255, 255, 255, 0.04);
        color: var(--text-strong);
        padding: 0.35rem 0.6rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
      button.active {
        border-color: var(--amber-strong);
        box-shadow: 0 0 0 1px rgba(255, 193, 7, 0.2);
        background: rgba(255, 193, 7, 0.08);
      }
      .picker input {
        background: rgba(255, 255, 255, 0.04);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.35rem 0.5rem;
        border-radius: 0.35rem;
      }
      .active-label {
        font-size: 0.85rem;
        color: var(--text-weak);
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DateFilterComponent {
  @Input() selection!: DateFilterState;
  @Input() label = 'Date range';
  @Output() selectionChange = new EventEmitter<DateFilterState>();

  protected readonly today = new Date().toISOString().slice(0, 10);

  get activeLabel() {
    if (!this.selection) {
      return 'Today';
    }
    if (this.selection.mode === 'all') {
      return 'All days';
    }
    if (this.selection.mode === 'day' && this.selection.day) {
      return `On ${this.selection.day}`;
    }
    return 'Today';
  }

  select(mode: DateFilterMode) {
    const next: DateFilterState = { mode, day: mode === 'day' ? this.selection?.day ?? this.today : undefined };
    this.selectionChange.emit(next);
  }

  onDayChange(event: Event) {
    const target = event.target as HTMLInputElement;
    const value = target.value;
    this.selectionChange.emit({ mode: 'day', day: value || this.today });
  }
}
