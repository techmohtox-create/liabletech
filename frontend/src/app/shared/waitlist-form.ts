import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { LeadService } from '../core/lead.service';
import { EmailText } from './email-text';

type Status = 'idle' | 'sending' | 'done' | 'error';

/** Early-access sign-up for products that are not released yet. */
@Component({
  selector: 'app-waitlist-form',
  imports: [ReactiveFormsModule, RouterLink, EmailText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status() === 'done') {
      <div class="alert alert-success" role="status">
        <strong>You're on the list.</strong> We'll email you before {{ productName() }} opens for early access.
        Reference {{ reference() }}.
      </div>
    } @else {
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="row">
          <div class="field">
            <label [for]="id() + '-name'">Name</label>
            <input class="input" [id]="id() + '-name'" formControlName="name" autocomplete="name" />
          </div>
          <div class="field">
            <label [for]="id() + '-email'">Work email</label>
            <input class="input" [id]="id() + '-email'" type="email" formControlName="email" autocomplete="email"
                   [class.invalid]="invalid('email')" required />
            @if (invalid('email')) { <span class="error-text">Please enter a valid email.</span> }
          </div>
        </div>
        <div class="field">
          <label [for]="id() + '-company'">Organisation <span class="muted">(optional)</span></label>
          <input class="input" [id]="id() + '-company'" formControlName="company" autocomplete="organization" />
        </div>
        <div class="honeypot" aria-hidden="true">
          <label [for]="id() + '-website'">Website</label>
          <input [id]="id() + '-website'" formControlName="website" tabindex="-1" autocomplete="off" />
        </div>
        @if (status() === 'error') {
          <div class="alert alert-error" role="alert">That didn't go through. Please try again, or email <app-email-text />.</div>
        }
        <button class="btn btn-primary" type="submit" [disabled]="status() === 'sending'">
          {{ status() === 'sending' ? 'Joining…' : 'Join the waitlist' }}
        </button>
        <p class="fine">We'll only email you about {{ productName() }}. See our <a routerLink="/privacy">privacy notice</a>.</p>
      </form>
    }
  `,
  styles: `
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
    .alert { margin-bottom: 14px; }
    .fine { margin: 12px 0 0; font-size: .85rem; color: var(--muted); }
    @media (max-width: 560px) { .row { grid-template-columns: 1fr; } }
  `,
})
export class WaitlistForm {
  private readonly leads = inject(LeadService);
  private readonly fb = inject(FormBuilder);

  readonly product = input.required<string>();
  readonly productName = input.required<string>();

  protected readonly id = computed(() => 'wl-' + this.product());
  protected readonly status = signal<Status>('idle');
  protected readonly reference = signal('');
  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.maxLength(100)],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
    company: ['', Validators.maxLength(150)],
    website: [''],
  });

  protected invalid(field: 'email'): boolean {
    const control = this.form.controls[field];
    return control.invalid && control.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.leads.joinWaitlist({ ...this.form.getRawValue(), product: this.product() }).subscribe({
      next: (response) => {
        this.reference.set(response.reference);
        this.status.set('done');
      },
      error: (_: HttpErrorResponse) => this.status.set('error'),
    });
  }
}
