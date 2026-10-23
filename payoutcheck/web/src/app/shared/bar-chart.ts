import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { compactInr, inr } from './format';

export interface Bar {
  label: string;
  value: number;
  detail?: string;
}

/**
 * Single-series vertical bars: one hue, thin marks with rounded data ends, a recessive grid and
 * a hover tooltip per bar. The values are also listed in a visually hidden table.
 */
@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="chart" (mouseleave)="hover.set(-1)">
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" [attr.aria-label]="title()" preserveAspectRatio="none">
        @for (tick of ticks(); track tick.y) {
          <line class="grid" [attr.x1]="left" [attr.x2]="width" [attr.y1]="tick.y" [attr.y2]="tick.y" />
          <text class="axis" [attr.x]="left - 8" [attr.y]="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
        }
        @for (bar of geometry(); track bar.label; let i = $index) {
          <rect class="hit" [attr.x]="bar.slotX" y="0" [attr.width]="bar.slotW" [attr.height]="plotBottom"
            (mouseenter)="hover.set(i)" (focus)="hover.set(i)" tabindex="0" [attr.aria-label]="bar.label + ': ' + money(bar.value)" />
          <path class="bar" [class.dim]="hover() !== -1 && hover() !== i" [attr.d]="bar.path" />
          <text class="axis" [attr.x]="bar.cx" [attr.y]="height - 6" text-anchor="middle">{{ bar.label }}</text>
        }
      </svg>
      @if (hover() !== -1 && geometry()[hover()]; as bar) {
        <div class="tip" [style.left.%]="(bar.cx / width) * 100">
          <strong>{{ money(bar.value) }}</strong>
          <span>{{ bar.label }}</span>
          @if (bar.detail) { <span class="muted">{{ bar.detail }}</span> }
        </div>
      }
      <table class="sr-only">
        <caption>{{ title() }}</caption>
        <tbody>
          @for (bar of bars(); track bar.label) {
            <tr><th scope="row">{{ bar.label }}</th><td>{{ money(bar.value) }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .chart { position: relative; }
    svg { width: 100%; height: 220px; display: block; overflow: visible; }
    .grid { stroke: var(--line); stroke-width: 1; }
    .axis { fill: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }
    .bar { fill: var(--chart-1); transition: opacity var(--dur) var(--ease); }
    .bar.dim { opacity: 0.45; }
    .hit { fill: transparent; cursor: default; outline: none; }
    .tip {
      position: absolute; top: 0; transform: translate(-50%, -100%); pointer-events: none; white-space: nowrap;
      background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-sm); box-shadow: var(--shadow);
      padding: 6px 10px; display: grid; gap: 1px; font-size: 12px; z-index: 2;
    }
    .tip strong { font-size: 14px; font-variant-numeric: tabular-nums; }
  `,
})
export class BarChart {
  readonly bars = input.required<Bar[]>();
  readonly title = input('Chart');
  protected readonly hover = signal(-1);

  protected readonly width = 640;
  protected readonly height = 220;
  protected readonly left = 52;
  protected readonly plotTop = 12;
  protected readonly plotBottom = 194;

  private readonly max = computed(() => niceMax(Math.max(0, ...this.bars().map((b) => b.value))));

  protected readonly ticks = computed(() => {
    const max = this.max();
    return [0, 0.5, 1].map((f) => ({
      y: this.plotBottom - f * (this.plotBottom - this.plotTop),
      label: max === 0 && f > 0 ? '' : compactInr(max * f),
    }));
  });

  protected readonly geometry = computed(() => {
    const bars = this.bars();
    const slot = (this.width - this.left) / Math.max(bars.length, 1);
    const barW = Math.min(36, slot * 0.5);
    const max = this.max() || 1;
    return bars.map((bar, i) => {
      const cx = this.left + slot * i + slot / 2;
      const h = Math.max(0, (bar.value / max) * (this.plotBottom - this.plotTop));
      const x = cx - barW / 2;
      const y = this.plotBottom - h;
      const r = Math.min(4, h, barW / 2);
      // Rounded top, square base anchored on the baseline.
      const path = h <= 0 ? '' : `M${x},${this.plotBottom} V${y + r} Q${x},${y} ${x + r},${y} H${x + barW - r} Q${x + barW},${y} ${x + barW},${y + r} V${this.plotBottom} Z`;
      return { ...bar, cx, slotX: this.left + slot * i, slotW: slot, path };
    });
  });

  protected money(value: number): string {
    return inr(value, true);
  }
}

function niceMax(value: number): number {
  if (value <= 0) return 0;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * magnitude >= value) ?? 10;
  return step * magnitude;
}
