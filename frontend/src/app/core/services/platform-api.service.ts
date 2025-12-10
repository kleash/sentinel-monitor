import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import {
  AckRequest,
  Alert,
  CreateWorkflowRequest,
  IngestRequest,
  ItemTimeline,
  StageAggregate,
  WallboardView,
  WorkflowSummary
} from '../models';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class PlatformApiService {
  private readonly baseUrl = environment.apiBaseUrl ?? '';

  constructor(private readonly http: HttpClient) {}

  getWallboard(): Observable<WallboardView> {
    return this.http.get<WallboardView>(`${this.baseUrl}/wallboard`).pipe(
      tap((wallboard) => console.debug('[api] wallboard received', wallboard))
    );
  }

  getWorkflows(): Observable<WorkflowSummary[]> {
    return this.http.get<WorkflowSummary[]>(`${this.baseUrl}/workflows`).pipe(
      tap((workflows) => console.debug('[api] workflows', workflows.length))
    );
  }

  getWorkflow(key: string): Observable<WorkflowSummary> {
    return this.http.get<WorkflowSummary>(`${this.baseUrl}/workflows/${key}`).pipe(
      tap((workflow) => console.debug('[api] workflow', workflow.key))
    );
  }

  getAggregates(workflowId: string, groupHash?: string, limit = 50): Observable<StageAggregate[]> {
    const params = new URLSearchParams();
    if (groupHash) {
      params.set('groupHash', groupHash);
    }
    params.set('limit', `${limit}`);
    const query = params.toString() ? `?${params.toString()}` : '';
    return this.http
      .get<StageAggregate[]>(`${this.baseUrl}/workflows/${workflowId}/aggregates${query}`)
      .pipe(tap((rows) => console.debug('[api] aggregates', workflowId, rows.length)));
  }

  getItem(correlationKey: string, workflowVersionId?: string): Observable<ItemTimeline> {
    const suffix = workflowVersionId ? `?workflowVersionId=${workflowVersionId}` : '';
    return this.http
      .get<ItemTimeline>(`${this.baseUrl}/items/${correlationKey}${suffix}`)
      .pipe(tap((item) => console.debug('[api] item', correlationKey, item.status)));
  }

  getAlerts(state?: string, limit = 100): Observable<Alert[]> {
    const params = new URLSearchParams();
    if (state) {
      params.set('state', state);
    }
    params.set('limit', `${limit}`);
    const query = params.toString() ? `?${params.toString()}` : '';
    return this.http
      .get<Alert[]>(`${this.baseUrl}/alerts${query}`)
      .pipe(
        map((rows) => rows.map((alert) => this.normalizeAlert(alert))),
        tap((rows) => console.debug('[api] alerts', rows.length))
      );
  }

  ackAlert(id: string, payload?: AckRequest) {
    return this.http
      .post<Alert>(`${this.baseUrl}/alerts/${id}/ack`, payload ?? {})
      .pipe(
        map((alert) => this.normalizeAlert(alert)),
        tap((alert) => console.debug('[api] ack', id, alert.state))
      );
  }

  suppressAlert(id: string, payload?: AckRequest) {
    return this.http
      .post<Alert>(`${this.baseUrl}/alerts/${id}/suppress`, payload ?? {})
      .pipe(
        map((alert) => this.normalizeAlert(alert)),
        tap((alert) => console.debug('[api] suppress', id, alert.state))
      );
  }

  resolveAlert(id: string, payload?: AckRequest) {
    return this.http
      .post<Alert>(`${this.baseUrl}/alerts/${id}/resolve`, payload ?? {})
      .pipe(
        map((alert) => this.normalizeAlert(alert)),
        tap((alert) => console.debug('[api] resolve', id, alert.state))
      );
  }

  createWorkflow(payload: CreateWorkflowRequest) {
    return this.http
      .post<WorkflowSummary>(`${this.baseUrl}/workflows`, payload)
      .pipe(tap((workflow) => console.debug('[api] created workflow', workflow.key)));
  }

  ingest(payload: IngestRequest) {
    return this.http
      .post(`${this.baseUrl}/ingest`, payload)
      .pipe(tap((event) => console.debug('[api] ingest', event)));
  }

  private normalizeAlert(alert: any): Alert {
    const correlationKey = alert.correlationKey ?? alert.correlation_key;
    const nodeKey = alert.nodeKey ?? alert.node_key;
    return {
      ...alert,
      correlationKey,
      nodeKey,
      workflowVersionId: alert.workflowVersionId ?? alert.workflow_version_id,
      dedupeKey: alert.dedupeKey ?? alert.dedupe_key,
      triggeredAt: alert.triggeredAt ?? alert.first_triggered_at ?? alert.triggered_at,
      lastTriggeredAt: alert.lastTriggeredAt ?? alert.last_triggered_at,
      ackedAt: alert.ackedAt ?? alert.acked_at,
      ackedBy: alert.ackedBy ?? alert.acked_by,
      suppressedUntil: alert.suppressedUntil ?? alert.suppressed_until,
      title: alert.title ?? nodeKey ?? 'Alert',
      reason: alert.reason ?? alert.state ?? ''
    };
  }
}
