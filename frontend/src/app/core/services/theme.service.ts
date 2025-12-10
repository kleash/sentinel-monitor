import { Injectable, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly wallboardModeSignal = signal(true);
  private readonly utcModeSignal = signal(environment.timezone === 'utc');
  private readonly timezoneSignal = computed(() => (this.utcModeSignal() ? 'UTC' : undefined));

  constructor() {
    this.applyWallboardClass();
    document.body.classList.toggle('utc-mode', this.utcModeSignal());
  }

  wallboardMode() {
    return this.wallboardModeSignal.asReadonly();
  }

  utcMode() {
    return this.utcModeSignal.asReadonly();
  }

  timezone() {
    return this.timezoneSignal;
  }

  toggleWallboard() {
    const next = !this.wallboardModeSignal();
    this.wallboardModeSignal.set(next);
    this.applyWallboardClass();
    if (next) {
      this.enterFullscreen();
    } else {
      this.exitFullscreen();
    }
  }

  toggleUtc() {
    this.utcModeSignal.set(!this.utcModeSignal());
    document.body.classList.toggle('utc-mode', this.utcModeSignal());
  }

  private applyWallboardClass() {
    document.body.classList.toggle('wallboard-mode', this.wallboardModeSignal());
  }

  private enterFullscreen() {
    const el = document.documentElement;
    if (document.fullscreenElement || !el.requestFullscreen) {
      return;
    }
    el.requestFullscreen().catch(() => {
      /* ignore fullscreen errors to avoid user disruption */
    });
  }

  private exitFullscreen() {
    if (!document.fullscreenElement || !document.exitFullscreen) {
      return;
    }
    document.exitFullscreen().catch(() => {
      /* ignore fullscreen errors */
    });
  }
}
