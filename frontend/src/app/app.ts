import { ChangeDetectionStrategy, Component, OnInit, computed } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AlertStripComponent } from './shared/components/alert-strip/alert-strip.component';
import { ToolbarComponent } from './shared/components/toolbar/toolbar.component';
import { LiveUpdateService } from './core/services/live-update.service';
import { PlatformApiService } from './core/services/platform-api.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AlertStripComponent, ToolbarComponent, NgIf],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App implements OnInit {
  protected readonly alerts = computed(() => this.live.alerts()());
  protected readonly updatedAt = computed(() => this.live.wallboard()()?.updatedAt ?? '');

  constructor(private readonly live: LiveUpdateService, private readonly api: PlatformApiService) {}

  ngOnInit(): void {
    this.live.start();
  }

  ack(id: string) {
    this.api.ackAlert(id).subscribe(() => this.live.refreshAlerts());
  }

  suppress(id: string) {
    this.api.suppressAlert(id).subscribe(() => this.live.refreshAlerts());
  }

  resolve(id: string) {
    this.api.resolveAlert(id).subscribe(() => this.live.refreshAlerts());
  }
}
