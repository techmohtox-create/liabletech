import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ContactInfo, ServiceOffering } from '../core/landing.model';
import { LandingService } from '../core/landing.service';

type Status = 'idle' | 'sending' | 'sent' | 'error';

@Component({
  selector: 'app-contact',
  imports: [ReactiveFormsModule],
  templateUrl: './contact.html',
  styleUrl: './contact.scss'
})
export class Contact {
  private readonly landing = inject(LandingService);
  private readonly fb = inject(FormBuilder);

  readonly contact = input.required<ContactInfo>();
  readonly services = input.required<ServiceOffering[]>();

  protected readonly status = signal<Status>('idle');
  protected readonly reference = signal('');
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
    company: ['', Validators.maxLength(150)],
    service: [''],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    website: ['']
  });

  protected invalid(field: 'name' | 'email' | 'message'): boolean {
    const control = this.form.controls[field];
    return (control.invalid && control.touched) || !!this.serverErrors()[field];
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.status.set('sending');
    this.serverErrors.set({});

    this.landing.submitContact(this.form.getRawValue()).subscribe({
      next: response => {
        this.reference.set(response.reference);
        this.status.set('sent');
        this.form.reset();
      },
      error: (error: HttpErrorResponse) => {
        this.serverErrors.set(error.status === 400 ? (error.error?.errors ?? {}) : {});
        this.status.set('error');
      }
    });
  }

  protected reset(): void {
    this.status.set('idle');
  }
}
