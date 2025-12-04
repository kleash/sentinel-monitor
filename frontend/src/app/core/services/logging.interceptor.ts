import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Lightweight interceptor that adds auth headers when available and logs timing for observability.
 */
export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const started = performance.now();
  const authHeader = auth.authorizationHeader();
  const request = authHeader ? req.clone({ setHeaders: { Authorization: authHeader } }) : req;

  return next(request).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          const elapsed = Math.round(performance.now() - started);
          // eslint-disable-next-line no-console
          console.debug(`[http] ${request.method} ${request.urlWithParams} -> ${event.status} (${elapsed}ms)`);
        }
      },
      error: (error) => {
        const elapsed = Math.round(performance.now() - started);
        // eslint-disable-next-line no-console
        console.error(`[http] ${request.method} ${request.urlWithParams} failed after ${elapsed}ms`, error);
      }
    })
  );
};
