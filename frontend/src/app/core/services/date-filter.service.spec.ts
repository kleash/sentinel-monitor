import { TestBed } from '@angular/core/testing';
import { DateFilterService } from './date-filter.service';

describe('DateFilterService', () => {
  let service: DateFilterService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DateFilterService);
  });

  it('defaults to today selection', () => {
    expect(service.selection().mode).toBe('today');
    expect(service.queryParams().date).toBe('today');
  });

  it('switches to all days', () => {
    service.setMode('all');
    expect(service.selection().mode).toBe('all');
    expect(service.queryParams().allDays).toBe('true');
  });
});
