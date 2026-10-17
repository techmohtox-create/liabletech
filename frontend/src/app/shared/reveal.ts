import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';

/**
 * Fades an element in when it scrolls into view. The hidden starting state only applies when
 * JavaScript runs (index.html adds a "js" class), so pre-rendered HTML is always readable.
 */
@Directive({ selector: '[data-reveal]' })
export class Reveal {
  constructor() {
    const element = inject(ElementRef).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      if (!('IntersectionObserver' in window)) {
        element.classList.add('in');
        return;
      }
      const observer = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              element.classList.add('in');
              observer.disconnect();
            }
          }
        },
        { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
      );
      observer.observe(element);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }
}
