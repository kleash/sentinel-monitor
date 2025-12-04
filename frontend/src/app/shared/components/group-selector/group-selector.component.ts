import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { NgFor } from '@angular/common';
import { WallboardGroupTile } from '../../../core/models';
import { StatusPillComponent } from '../status-pill/status-pill.component';

@Component({
  selector: 'app-group-selector',
  standalone: true,
  imports: [NgFor, StatusPillComponent],
  template: `
    <div class="group-selector">
      <button
        type="button"
        *ngFor="let group of groups"
        [class.active]="group.groupHash === active"
        (click)="select.emit(group.groupHash)"
      >
        <div class="label">{{ group.label }}</div>
        <app-status-pill [label]="group.status.toUpperCase()" [severity]="group.status"></app-status-pill>
      </button>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .group-selector {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 0.5rem;
      }
      button {
        background: rgba(255, 255, 255, 0.03);
        border: 1px solid var(--border-strong);
        border-radius: 0.7rem;
        padding: 0.6rem;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: var(--text-strong);
      }
      button.active {
        border-color: var(--amber-strong);
        box-shadow: 0 0 12px rgba(255, 193, 7, 0.2);
      }
      .label {
        font-weight: 700;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GroupSelectorComponent {
  @Input() groups: WallboardGroupTile[] = [];
  @Input() active?: string;
  @Output() select = new EventEmitter<string>();
}
