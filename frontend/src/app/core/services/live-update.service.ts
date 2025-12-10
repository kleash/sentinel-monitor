import { Injectable, OnDestroy, signal } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { Alert, WallboardView } from '../models';
import { PlatformApiService } from './platform-api.service';

@Injectable({ providedIn: 'root' })
export class LiveUpdateService implements OnDestroy {
  private readonly wallboardSignal = signal<WallboardView | null>(null);
  private readonly alertsSignal = signal<Alert[]>([]);
  private readonly subscriptions: Subscription[] = [];
  private wallboardParams: Record<string, string | number | boolean | undefined> = {};
  private started = false;

  constructor(private readonly api: PlatformApiService) {}

  wallboard() {
    return this.wallboardSignal.asReadonly();
  }

  alerts() {
    return this.alertsSignal.asReadonly();
  }

  start() {
    if (this.started) {
      return;
    }
    this.started = true;
    this.refreshWallboard();
    this.refreshAlerts();
    this.subscriptions.push(
      interval(environment.wallboardAutoRefreshMs)
        .pipe(switchMap(() => this.api.getWallboard(this.wallboardParams)))
        .subscribe((data) => this.wallboardSignal.set(data)),
      interval(environment.pollIntervalMs)
        .pipe(switchMap(() => this.api.getAlerts('open')))
        .subscribe((alerts) => this.alertsSignal.set(alerts))
    );
  }

  setWallboardParams(params: Record<string, string | number | boolean | undefined>) {
    this.wallboardParams = params ?? {};
    if (this.started) {
      this.refreshWallboard();
    }
  }

  refreshWallboard() {
    this.api.getWallboard(this.wallboardParams).subscribe((data) => this.wallboardSignal.set(data));
  }

  refreshAlerts() {
    this.api.getAlerts().subscribe((alerts) => this.alertsSignal.set(alerts));
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
  }
}
