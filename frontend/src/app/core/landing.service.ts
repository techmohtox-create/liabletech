import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ContactRequest, ContactResponse, LandingContent } from './landing.model';

@Injectable({ providedIn: 'root' })
export class LandingService {
  private readonly http = inject(HttpClient);

  readonly content = signal<LandingContent | null>(null);
  readonly failed = signal(false);

  load(): void {
    this.failed.set(false);
    this.http.get<LandingContent>(`${environment.apiBaseUrl}/api/landing`).subscribe({
      next: content => this.content.set(content),
      error: () => this.failed.set(true)
    });
  }

  submitContact(request: ContactRequest): Observable<ContactResponse> {
    return this.http.post<ContactResponse>(`${environment.apiBaseUrl}/api/contact`, request);
  }
}
