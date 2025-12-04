import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PlatformApiService } from '../../core/services/platform-api.service';

@Component({
  selector: 'app-ingest-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="ingest">
      <header>
        <div class="title">Ingest Simulator</div>
      </header>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <div class="grid">
          <label>
            Event ID
            <input formControlName="eventId" placeholder="uuid" />
          </label>
          <label>
            Source System
            <input formControlName="sourceSystem" placeholder="sys1" />
          </label>
          <label>
            Event Type
            <input formControlName="eventType" placeholder="TRADE_INGEST" />
          </label>
          <label>
            Event Time (UTC)
            <input formControlName="eventTime" type="datetime-local" />
          </label>
          <label>
            Correlation Key
            <input formControlName="correlationKey" placeholder="TR123" />
          </label>
          <label>
            Workflow Key
            <input formControlName="workflowKey" placeholder="trade-lifecycle" />
          </label>
        </div>
        <label>
          Group (JSON)
          <textarea formControlName="group" rows="3" placeholder='{"book":"EQD"}'></textarea>
        </label>
        <label>
          Payload (JSON)
          <textarea formControlName="payload" rows="4" placeholder='{"amount":1000}'></textarea>
        </label>
        <button type="submit" [disabled]="form.invalid">Send</button>
        <div class="result" *ngIf="message">{{ message }}</div>
      </form>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .ingest {
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 1rem;
        background: rgba(255, 255, 255, 0.02);
      }
      .title {
        font-size: 1.3rem;
        font-weight: 800;
        margin-bottom: 0.5rem;
      }
      form {
        display: grid;
        gap: 0.6rem;
      }
      .grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 0.5rem;
      }
      label {
        display: grid;
        gap: 0.2rem;
      }
      input,
      textarea {
        background: rgba(0, 0, 0, 0.3);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.4rem;
        border-radius: 0.35rem;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.45rem 0.7rem;
        border-radius: 0.4rem;
        cursor: pointer;
        width: fit-content;
      }
      .result {
        color: var(--green-strong);
        font-weight: 700;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IngestPageComponent {
  message = '';
  form: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly api: PlatformApiService,
    private readonly cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      eventId: this.fb.control(''),
      sourceSystem: this.fb.control('ops-ui', { validators: [Validators.required] }),
      eventType: this.fb.control('TRADE_INGEST', { validators: [Validators.required] }),
      eventTime: this.fb.control(new Date().toISOString().slice(0, 16), { validators: [Validators.required] }),
      correlationKey: this.fb.control('TR123', { validators: [Validators.required] }),
      workflowKey: this.fb.control('trade-lifecycle', { validators: [Validators.required] }),
      group: this.fb.control('{"book":"EQD","region":"NY"}'),
      payload: this.fb.control('{"note":"simulated"}')
    });
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    try {
      const payload = {
        ...this.form.value,
        group: this.form.value.group ? JSON.parse(this.form.value.group) : undefined,
        payload: this.form.value.payload ? JSON.parse(this.form.value.payload) : undefined
      };
      this.api.ingest(payload as any).subscribe(() => {
        this.message = 'Event sent for ingestion';
        this.cdr.markForCheck();
      });
    } catch (err) {
      this.message = 'Invalid JSON payload';
      this.cdr.markForCheck();
    }
  }
}
