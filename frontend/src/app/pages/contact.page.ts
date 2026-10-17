import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BUDGETS, NEEDS, STAGES, TIMELINES } from '../core/content';
import { leadSource, LeadService } from '../core/lead.service';
import { Seo } from '../core/seo.service';
import { SITE } from '../core/site';
import { EmailText } from '../shared/email-text';
import { Icon } from '../shared/icon';

type Status = 'idle' | 'sending' | 'sent' | 'error';

@Component({
  selector: 'app-contact-page',
  imports: [ReactiveFormsModule, RouterLink, Icon, EmailText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './contact.page.html',
  styleUrl: './contact.page.scss',
})
export class ContactPage {
  private readonly leads = inject(LeadService);
  private readonly fb = inject(FormBuilder);

  /** Pre-selects a need when arriving from a service or product page (?interest=slug). */
  readonly interest = input<string>();

  protected readonly needs = NEEDS;
  protected readonly stages = STAGES;
  protected readonly timelines = TIMELINES;
  protected readonly budgets = BUDGETS;
  protected readonly email = SITE.email;
  protected readonly stepTitles = ['What you need', 'Where you are', 'Timing and budget', 'Your details'];

  protected readonly step = signal(0);
  protected readonly need = signal('');
  protected readonly stage = signal('');
  protected readonly timeline = signal('');
  protected readonly budget = signal('');
  protected readonly status = signal<Status>('idle');
  protected readonly reference = signal('');
  protected readonly serverErrors = signal<Record<string, string>>({});
  protected readonly needLabel = computed(() => NEEDS.find((n) => n.id === this.need())?.label ?? '');

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
    company: ['', Validators.maxLength(150)],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    website: [''],
  });

  constructor() {
    inject(Seo).set({
      title: 'Contact LiableTech | Discuss your project',
      description:
        'Tell us what you need built, fixed or automated. Answer four quick questions and get a reply within one business day, then a written plan and estimate.',
      path: '/contact',
      crumbs: [{ name: 'Contact', path: '/contact' }],
    });

    effect(() => {
      const wanted = this.interest();
      if (wanted && NEEDS.some((n) => n.id === wanted) && !this.need()) {
        this.need.set(wanted);
      }
    });
  }

  protected choose(kind: 'need' | 'stage' | 'timeline' | 'budget', value: string): void {
    this[kind].set(value);
    if (kind === 'need' || kind === 'stage') {
      this.next();
    }
  }

  protected next(): void {
    this.step.update((s) => Math.min(s + 1, 3));
  }

  protected back(): void {
    this.step.update((s) => Math.max(s - 1, 0));
  }

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
    const value = this.form.getRawValue();
    this.leads
      .enquire({
        ...value,
        service: this.needLabel(),
        stage: this.stage(),
        timeline: this.timeline(),
        budget: this.budget(),
        source: leadSource('/contact'),
      })
      .subscribe({
        next: (response) => {
          this.reference.set(response.reference);
          this.status.set('sent');
          if (typeof window !== 'undefined') {
            window.scrollTo({ top: 0, behavior: 'smooth' });
          }
        },
        error: (error: HttpErrorResponse) => {
          this.serverErrors.set(error.status === 400 ? (error.error?.errors ?? {}) : {});
          this.status.set('error');
        },
      });
  }

  protected restart(): void {
    this.form.reset();
    this.need.set('');
    this.stage.set('');
    this.timeline.set('');
    this.budget.set('');
    this.step.set(0);
    this.status.set('idle');
  }
}
