import { Injectable, computed, signal } from '@angular/core';
import { DateFilterMode, DateFilterState } from '../models';

@Injectable({ providedIn: 'root' })
export class DateFilterService {
  private readonly selectionSignal = signal<DateFilterState>({ mode: 'today', day: this.today() });
  readonly selection = this.selectionSignal.asReadonly();
  readonly label = computed(() => this.describe(this.selectionSignal()));
  readonly queryParams = computed(() => this.toQuery(this.selectionSignal()));

  setMode(mode: DateFilterMode, day?: string) {
    if (mode === 'day' && !day) {
      day = this.selectionSignal().day ?? this.today();
    }
    this.selectionSignal.set({ mode, day });
  }

  setDay(day: string) {
    const current = this.selectionSignal();
    this.selectionSignal.set({ mode: current.mode === 'all' ? 'day' : current.mode, day });
  }

  private today() {
    return new Date().toISOString().slice(0, 10);
  }

  private describe(selection: DateFilterState) {
    if (selection.mode === 'all') {
      return 'All days';
    }
    if (selection.mode === 'day' && selection.day) {
      return `On ${selection.day}`;
    }
    return 'Today';
  }

  private toQuery(selection: DateFilterState) {
    if (selection.mode === 'all') {
      return { allDays: 'true' };
    }
    if (selection.mode === 'day' && selection.day) {
      return { date: selection.day };
    }
    return { date: 'today' };
  }
}
