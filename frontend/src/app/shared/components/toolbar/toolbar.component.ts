import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { ThemeService } from '../../../core/services/theme.service';
import { StatusPillComponent } from '../status-pill/status-pill.component';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, StatusPillComponent],
  template: `
    <header class="toolbar">
      <div class="brand">
        <span class="wordmark">Sentinel Command Center</span>
        <app-status-pill label="LIVE" severity="amber" [meta]="updatedAtLabel"></app-status-pill>
      </div>
      <nav>
        <a routerLink="/wallboard" routerLinkActive="active">Wallboard</a>
        <a routerLink="/alerts" routerLinkActive="active">Alerts</a>
        <a routerLink="/rules" routerLinkActive="active">Rules</a>
        <a routerLink="/ingest" routerLinkActive="active">Ingest</a>
      </nav>
      <div class="toggles">
        <button type="button" (click)="theme.toggleWallboard()">Wallboard Mode</button>
        <button type="button" (click)="theme.toggleUtc()">UTC</button>
      </div>
    </header>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .toolbar {
        display: grid;
        grid-template-columns: 1fr auto auto;
        align-items: center;
        padding: 0.75rem 1rem;
        background: linear-gradient(90deg, rgba(0, 0, 0, 0.8), rgba(14, 17, 32, 0.8));
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        margin-bottom: 0.75rem;
        box-shadow: 0 12px 24px rgba(0, 0, 0, 0.35);
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        font-weight: 800;
        letter-spacing: 0.08em;
      }
      nav {
        display: flex;
        gap: 1rem;
      }
      nav a {
        color: var(--text-strong);
        text-decoration: none;
        padding: 0.35rem 0.5rem;
        border-radius: 0.35rem;
      }
      nav a.active {
        background: rgba(255, 255, 255, 0.08);
        border: 1px solid var(--amber-strong);
      }
      .toggles {
        display: flex;
        gap: 0.5rem;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        color: var(--text-strong);
        border: 1px solid var(--border-strong);
        padding: 0.4rem 0.6rem;
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
export class ToolbarComponent {
  @Input() updatedAtLabel = '';

  constructor(public readonly theme: ThemeService) {}
}
