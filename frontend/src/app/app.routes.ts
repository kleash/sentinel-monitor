import { Routes } from '@angular/router';
import { WallboardPageComponent } from './features/wallboard/wallboard-page.component';
import { WorkflowPageComponent } from './features/workflow/workflow-page.component';
import { ItemPageComponent } from './features/item/item-page.component';
import { AlertsPageComponent } from './features/alerts/alerts-page.component';
import { RulesPageComponent } from './features/rules/rules-page.component';
import { IngestPageComponent } from './features/ingest/ingest-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'wallboard' },
  { path: 'wallboard', component: WallboardPageComponent },
  { path: 'workflow/:key', component: WorkflowPageComponent },
  { path: 'item/:correlationKey', component: ItemPageComponent },
  { path: 'alerts', component: AlertsPageComponent },
  { path: 'rules', component: RulesPageComponent },
  { path: 'ingest', component: IngestPageComponent }
];
