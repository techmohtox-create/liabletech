import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Session } from './session';

export const signedIn: CanActivateFn = () => {
  const session = inject(Session);
  return session.me() ? true : inject(Router).createUrlTree(['/login']);
};

export const signedOut: CanActivateFn = () => {
  const session = inject(Session);
  return session.me() ? inject(Router).createUrlTree(['/']) : true;
};

export const ownerOnly: CanActivateFn = () => {
  const session = inject(Session);
  return session.isOwner() ? true : inject(Router).createUrlTree(['/']);
};

export const sessionExpiry: HttpInterceptorFn = (request, next) => {
  const session = inject(Session);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !request.url.startsWith('/api/auth/')) {
        session.expired();
      }
      return throwError(() => error);
    }),
  );
};
