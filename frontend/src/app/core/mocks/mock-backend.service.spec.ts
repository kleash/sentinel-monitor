import { TestBed } from '@angular/core/testing';
import { MockBackendService } from './mock-backend.service';

describe('MockBackendService', () => {
  let service: MockBackendService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MockBackendService);
  });

  it('creates a workflow and exposes it via wallboard', () => {
    const initial = service.getWallboard().workflows.length;
    const wf = service.createWorkflow({
      name: 'Spec Flow',
      key: 'spec-flow',
      createdBy: 'spec',
      graph: { nodes: [{ key: 'start', eventType: 'A', start: true }], edges: [] }
    });
    expect(wf.key).toEqual('spec-flow');
    expect(service.getWallboard().workflows.length).toBeGreaterThan(initial);
  });

  it('acknowledges alerts', () => {
    const alert = service.getAlerts()[0];
    const updated = service.ackAlert(alert.id, { reason: 'test' });
    expect(updated?.state).toEqual('ack');
    expect(updated?.reason).toEqual('test');
  });

  it('returns correlation instances for a workflow', () => {
    const page = service.getCorrelations('trade-lifecycle', { size: 5 });
    expect(page.items.length).toBeGreaterThan(0);
    expect(page.items[0].correlationId).toBeDefined();
  });
});
