import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly wallboardModeSignal = signal(true);
  private readonly utcModeSignal = signal(false);

  wallboardMode() {
    return this.wallboardModeSignal.asReadonly();
  }

  utcMode() {
    return this.utcModeSignal.asReadonly();
  }

  toggleWallboard() {
    this.wallboardModeSignal.set(!this.wallboardModeSignal());
  }

  toggleUtc() {
    this.utcModeSignal.set(!this.utcModeSignal());
    document.body.classList.toggle('utc-mode', this.utcModeSignal());
  }
}
