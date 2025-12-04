import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule, NgFor, NgIf } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { PlatformApiService } from '../../core/services/platform-api.service';
import { WorkflowSummary } from '../../core/models';
import { GraphCanvasComponent } from '../../shared/components/graph-canvas/graph-canvas.component';

@Component({
  selector: 'app-rules-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NgIf, NgFor, GraphCanvasComponent],
  template: `
    <section class="rules">
      <div class="pane">
        <header>
          <div class="title">Workflow Catalog</div>
          <div class="meta">{{ workflows.length }} total</div>
        </header>
        <div class="list">
          <article *ngFor="let wf of workflows" class="wf">
            <div class="name">{{ wf.name }}</div>
            <div class="key">{{ wf.key }}</div>
            <div class="version">Active: {{ wf.activeVersion }}</div>
          </article>
        </div>
      </div>
      <div class="pane form-pane">
        <header>
          <div class="title">Create Workflow</div>
        </header>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label>
            Name
            <input formControlName="name" placeholder="Trade Lifecycle" />
          </label>
          <label>
            Key
            <input formControlName="key" placeholder="trade-lifecycle" />
          </label>
          <label>
            Created By
            <input formControlName="createdBy" placeholder="ops-user" />
          </label>
          <label>
            Runbook URL
            <input formControlName="runbookUrl" placeholder="https://runbooks/..." />
          </label>
          <label>
            Group Dimensions (comma separated)
            <input formControlName="groupDimensions" placeholder="book,region" />
          </label>

          <div class="section">
            <div class="section-title">Nodes</div>
            <div class="group-row" *ngFor="let node of nodes.controls; let i = index" [formGroupName]="i">
              <input formControlName="key" placeholder="ingest" />
              <input formControlName="eventType" placeholder="TRADE_INGEST" />
              <label><input type="checkbox" formControlName="start" /> Start</label>
              <label><input type="checkbox" formControlName="terminal" /> Terminal</label>
              <button type="button" (click)="removeNode(i)">Remove</button>
            </div>
            <button type="button" (click)="addNode()">Add Node</button>
          </div>

          <div class="section">
            <div class="section-title">Edges</div>
            <div class="group-row" *ngFor="let edge of edges.controls; let i = index" [formGroupName]="i">
              <input formControlName="from" placeholder="ingest" />
              <input formControlName="to" placeholder="sys2-verify" />
              <input type="number" formControlName="maxLatencySec" placeholder="300" />
              <select formControlName="severity">
                <option value="green">green</option>
                <option value="amber">amber</option>
                <option value="red">red</option>
              </select>
              <button type="button" (click)="removeEdge(i)">Remove</button>
            </div>
            <button type="button" (click)="addEdge()">Add Edge</button>
          </div>

          <button type="submit" [disabled]="form.invalid">Create</button>
        </form>

        <app-graph-canvas
          *ngIf="form.valid"
          [graph]="previewGraph"
          [statusByNode]="{}"
        ></app-graph-canvas>
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .rules {
        display: grid;
        grid-template-columns: 1fr 1.5fr;
        gap: 1rem;
      }
      .pane {
        border: 1px solid var(--border-strong);
        border-radius: 1rem;
        padding: 1rem;
        background: rgba(255, 255, 255, 0.02);
      }
      header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.6rem;
      }
      .list {
        display: grid;
        gap: 0.5rem;
      }
      .wf {
        border: 1px solid var(--border-strong);
        border-radius: 0.75rem;
        padding: 0.5rem;
      }
      form {
        display: grid;
        gap: 0.5rem;
      }
      label {
        display: grid;
        gap: 0.2rem;
      }
      input,
      select {
        background: rgba(0, 0, 0, 0.3);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.4rem;
        border-radius: 0.35rem;
      }
      .group-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
        gap: 0.35rem;
        align-items: center;
      }
      .section {
        border-top: 1px dashed var(--border-strong);
        padding-top: 0.5rem;
      }
      .section-title {
        font-weight: 700;
        margin-bottom: 0.3rem;
      }
      button {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border-strong);
        color: var(--text-strong);
        padding: 0.4rem 0.6rem;
        border-radius: 0.4rem;
        cursor: pointer;
      }
      .form-pane app-graph-canvas {
        margin-top: 0.8rem;
        display: block;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RulesPageComponent implements OnInit {
  workflows: WorkflowSummary[] = [];
  form: FormGroup;

  constructor(private readonly api: PlatformApiService, private readonly fb: FormBuilder) {
    this.form = this.fb.group({
      name: this.fb.control('', { validators: [Validators.required] }),
      key: this.fb.control('', { validators: [Validators.required] }),
      createdBy: this.fb.control('ops-user', { validators: [Validators.required] }),
      runbookUrl: this.fb.control(''),
      groupDimensions: this.fb.control('book,region'),
      nodes: this.fb.array([
        this.fb.group({
          key: this.fb.control('ingest', { nonNullable: true }),
          eventType: this.fb.control('TRADE_INGEST', { nonNullable: true }),
          start: this.fb.control(true, { nonNullable: true }),
          terminal: this.fb.control(false, { nonNullable: true })
        })
      ]),
      edges: this.fb.array([
        this.fb.group({
          from: this.fb.control('ingest', { nonNullable: true }),
          to: this.fb.control('sys2-verify', { nonNullable: true }),
          maxLatencySec: this.fb.control(300, { nonNullable: false }),
          severity: this.fb.control<'green' | 'amber' | 'red'>('amber', { nonNullable: true })
        })
      ])
    });
  }

  get nodes() {
    return this.form.get('nodes') as FormArray<FormGroup>;
  }

  get edges() {
    return this.form.get('edges') as FormArray<FormGroup>;
  }

  get previewGraph() {
    return {
      nodes: this.nodes.value as any[],
      edges: this.edges.value as any[],
      groupDimensions: (this.form.get('groupDimensions') as FormControl<string>).value
        .split(',')
        .map((g) => g.trim())
    };
  }

  ngOnInit(): void {
    this.load();
    if (this.edges.length === 0) {
      this.addEdge();
    }
  }

  load() {
    this.api.getWorkflows().subscribe((workflows) => (this.workflows = workflows));
  }

  addNode() {
    this.nodes.push(
      this.fb.group({
        key: this.fb.control('', { nonNullable: true }),
        eventType: this.fb.control('', { nonNullable: true }),
        start: this.fb.control(false, { nonNullable: true }),
        terminal: this.fb.control(false, { nonNullable: true })
      })
    );
  }

  removeNode(index: number) {
    this.nodes.removeAt(index);
  }

  addEdge() {
    this.edges.push(
      this.fb.group({
        from: this.fb.control('', { nonNullable: true }),
        to: this.fb.control('', { nonNullable: true }),
        maxLatencySec: this.fb.control(300, { nonNullable: false }),
        severity: this.fb.control<'green' | 'amber' | 'red'>('amber', { nonNullable: true })
      })
    );
  }

  removeEdge(index: number) {
    this.edges.removeAt(index);
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.value;
    const payload = {
      name: value.name!,
      key: value.key!,
      createdBy: value.createdBy!,
      runbookUrl: value.runbookUrl ?? '',
      graph: {
        nodes: value.nodes ?? [],
        edges: value.edges ?? [],
        groupDimensions: (value.groupDimensions ?? '')
          .split(',')
          .map((g: string) => g.trim())
          .filter(Boolean)
      }
    };
    this.api.createWorkflow(payload).subscribe(() => this.load());
  }
}
