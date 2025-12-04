import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { MockBackendService } from '../mocks/mock-backend.service';
import { AckRequest, CreateWorkflowRequest, IngestRequest } from '../models';

const JSON_DELAY = 100;

/**
 * Intercepts HTTP calls when mockApi is enabled so the UI can run without a backend.
 */
export const mockApiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!environment.mockApi) {
    return next(req);
  }

  const mock = inject(MockBackendService);
  const path = stripBase(req.url);

  if (req.method === 'GET' && path === '/wallboard') {
    return respond(mock.getWallboard());
  }
  if (req.method === 'GET' && path === '/workflows') {
    return respond(mock.getWorkflows());
  }
  if (req.method === 'GET' && path.startsWith('/workflows/')) {
    const key = path.split('/')[2];
    const workflow = mock.getWorkflow(key);
    if (!workflow) {
      return notFound();
    }
    return respond(workflow);
  }
  if (req.method === 'GET' && path.includes('/aggregates')) {
    const workflowId = path.split('/')[2];
    const url = new URL(req.url, 'http://localhost');
    const groupHash = url.searchParams.get('groupHash') ?? undefined;
    return respond(mock.getAggregates(workflowId, groupHash));
  }
  if (req.method === 'GET' && path.startsWith('/items/')) {
    const correlationKey = path.split('/')[2];
    const item = mock.getItem(correlationKey);
    if (!item) {
      return notFound();
    }
    return respond(item);
  }
  if (req.method === 'GET' && path.startsWith('/alerts')) {
    const url = new URL(req.url, 'http://localhost');
    const state = url.searchParams.get('state') ?? undefined;
    return respond(mock.getAlerts(state));
  }
  if (req.method === 'POST' && path.match(/^\/alerts\/[^/]+\/ack/)) {
    const id = path.split('/')[2];
    const updated = mock.ackAlert(id, req.body as AckRequest);
    return updated ? respond(updated) : notFound();
  }
  if (req.method === 'POST' && path.match(/^\/alerts\/[^/]+\/suppress/)) {
    const id = path.split('/')[2];
    const updated = mock.suppressAlert(id, req.body as AckRequest);
    return updated ? respond(updated) : notFound();
  }
  if (req.method === 'POST' && path.match(/^\/alerts\/[^/]+\/resolve/)) {
    const id = path.split('/')[2];
    const updated = mock.resolveAlert(id, req.body as AckRequest);
    return updated ? respond(updated) : notFound();
  }
  if (req.method === 'POST' && path === '/workflows') {
    return respond(mock.createWorkflow(req.body as CreateWorkflowRequest));
  }
  if (req.method === 'POST' && path === '/ingest') {
    return respond(mock.ingest(req.body as IngestRequest));
  }

  return next(req);
};

function respond<T>(body: T): Observable<HttpResponse<T>> {
  return of(new HttpResponse({ status: 200, body })).pipe(delay(JSON_DELAY));
}

function notFound(): Observable<never> {
  return throwError(() => new HttpResponse({ status: 404 }));
}

function stripBase(url: string): string {
  const noQuery = url.split('?')[0];
  const normalized = noQuery.replace(environment.apiBaseUrl, '');
  if (!normalized.startsWith('/')) {
    return `/${normalized}`;
  }
  return normalized;
}
