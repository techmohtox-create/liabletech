import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export interface EnquiryRequest {
  name: string;
  email: string;
  company: string;
  service: string;
  message: string;
  stage: string;
  timeline: string;
  budget: string;
  source: string;
  /** Honeypot: hidden in the form, so only bots fill it in. */
  website: string;
}

export interface WaitlistRequest {
  name: string;
  email: string;
  company: string;
  product: string;
  website: string;
}

export interface LeadResponse {
  reference: string;
  message: string;
}

/** Sends enquiries and waitlist sign-ups to the BFF. */
@Injectable({ providedIn: 'root' })
export class LeadService {
  private readonly http = inject(HttpClient);

  enquire(request: EnquiryRequest): Observable<LeadResponse> {
    return this.http.post<LeadResponse>(`${environment.apiBaseUrl}/api/contact`, request);
  }

  joinWaitlist(request: WaitlistRequest): Observable<LeadResponse> {
    return this.http.post<LeadResponse>(`${environment.apiBaseUrl}/api/waitlist`, request);
  }
}

/** Where a lead came from: the page plus any campaign tags on the first visit. */
export function leadSource(path: string): string {
  if (typeof window === 'undefined') {
    return path;
  }
  let first = '';
  try {
    first = window.sessionStorage.getItem('lt.first') ?? '';
  } catch {
    // storage blocked: keep the page only
  }
  return (first ? `${path} | ${first}` : path).slice(0, 200);
}

/** Remember campaign tags (utm_*) from the landing URL for the rest of the visit. */
export function rememberCampaign(): void {
  if (typeof window === 'undefined') {
    return;
  }
  try {
    if (window.sessionStorage.getItem('lt.first')) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const tags = ['utm_source', 'utm_medium', 'utm_campaign']
      .map((key) => (params.get(key) ? `${key.slice(4)}=${params.get(key)}` : ''))
      .filter(Boolean);
    const referrer = document.referrer && !document.referrer.includes('liabletech.com') ? `ref=${new URL(document.referrer).hostname}` : '';
    const value = [...tags, referrer].filter(Boolean).join(' ');
    window.sessionStorage.setItem('lt.first', value || 'direct');
  } catch {
    // storage blocked: nothing to remember
  }
}
