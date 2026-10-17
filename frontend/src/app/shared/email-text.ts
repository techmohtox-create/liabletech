import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { SITE } from '../core/site';

/**
 * Prints an email address with the "@" in its own element. Cloudflare's email obfuscation only
 * rewrites addresses written as one piece of text; when it rewrites one inside a pre-rendered page,
 * Angular's hydration no longer recognises the text and shows the address twice.
 */
@Component({
  selector: 'app-email-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `{{ local() }}<span>&#64;</span>{{ domain() }}`,
})
export class EmailText {
  readonly address = input<string>(SITE.email);

  protected readonly local = computed(() => this.address().split('@')[0]);
  protected readonly domain = computed(() => this.address().split('@')[1] ?? '');
}
