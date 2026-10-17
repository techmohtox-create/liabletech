import { ChangeDetectionStrategy, Component } from '@angular/core';

interface Module {
  x: number;
  y: number;
  w: number;
  h: number;
  label: string;
  path: string;
}

/**
 * The brand illustration: navy core blocks forming the "L", the cyan keystone, and outlined
 * integration modules joined by dotted connectors. Data dots travel along the connectors into the
 * keystone. Hovering a module highlights its connector. Motion stops under reduced-motion.
 */
@Component({
  selector: 'app-hero-art',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 640 500" role="img" aria-labelledby="hero-art-title">
      <title id="hero-art-title">Your systems connected through one reliable core</title>
      <defs>
        <pattern id="dots" width="22" height="22" patternUnits="userSpaceOnUse">
          <circle cx="2" cy="2" r="1.6" fill="#0B1B33" fill-opacity=".09" />
        </pattern>
      </defs>
      <rect width="640" height="500" fill="url(#dots)" />

      @for (m of modules; track m.label; let i = $index) {
        <g class="mod" [style.--d]="i">
          <path class="wire" [attr.d]="m.path" />
          <path class="wire-hl" [attr.d]="m.path" />
          <circle class="packet" r="4">
            <animateMotion [attr.dur]="(3.2 + i * 0.45) + 's'" [attr.begin]="(1.2 + i * 0.5) + 's'"
                           repeatCount="indefinite" [attr.path]="m.path" keyPoints="1;0" keyTimes="0;1" calcMode="linear" />
          </circle>
          <rect class="box" [attr.x]="m.x" [attr.y]="m.y" [attr.width]="m.w" [attr.height]="m.h" rx="7" />
          <rect class="pip" [attr.x]="m.x + 8" [attr.y]="m.y + 8" width="6" height="6" rx="1.2" />
          <text [attr.x]="m.x + m.w / 2" [attr.y]="m.y + m.h / 2 + 5">{{ m.label }}</text>
        </g>
      }

      <rect class="blk stem" x="170" y="50" width="76" height="262" rx="12" />
      <rect class="blk foot" x="170" y="350" width="330" height="80" rx="12" />
      <rect class="key" x="272" y="222" width="90" height="90" rx="12" />
    </svg>
  `,
  styles: `
    :host { display: block; }
    svg { width: 100%; height: auto; display: block; overflow: visible; }
    .blk { fill: #0B1B33; animation: rise 700ms cubic-bezier(.2,.8,.2,1) both; }
    .foot { animation-delay: 120ms; animation-name: slide; }
    .key { fill: #16C7E3; transform-box: fill-box; transform-origin: center; animation: pop 600ms 380ms cubic-bezier(.2,.8,.2,1) both; }
    .wire { fill: none; stroke: #1F5FE0; stroke-opacity: .38; stroke-width: 2.4; stroke-dasharray: 2 9; stroke-linecap: round; }
    .wire-hl { fill: none; stroke: #1F5FE0; stroke-width: 2.4; stroke-linecap: round; stroke-dasharray: 600; stroke-dashoffset: 600;
      transition: stroke-dashoffset 600ms cubic-bezier(.2,.8,.2,1); }
    .box { fill: #fff; stroke: #1F5FE0; stroke-width: 2; transition: fill 200ms ease-out; }
    .pip { fill: #1F5FE0; }
    text { font: 600 12.5px Inter, 'Segoe UI', sans-serif; fill: #0B1B33; text-anchor: middle; pointer-events: none; }
    .packet { fill: #16C7E3; opacity: .95; }
    .mod { animation: fade 600ms cubic-bezier(.2,.8,.2,1) both; animation-delay: calc(500ms + var(--d) * 90ms); cursor: default; }
    .mod:hover .wire-hl { stroke-dashoffset: 0; }
    .mod:hover .box { fill: #EBF1FD; }
    @keyframes rise { from { opacity: 0; transform: translateY(-18px); } }
    @keyframes slide { from { opacity: 0; transform: translateX(-18px); } }
    @keyframes pop { from { opacity: 0; transform: scale(.5); } }
    @keyframes fade { from { opacity: 0; } }
    @media (prefers-reduced-motion: reduce) {
      .packet { display: none; }
      .blk, .key, .mod { animation: none; }
    }
  `,
})
export class HeroArt {
  protected readonly modules: Module[] = [
    { x: 440, y: 40, w: 84, h: 52, label: 'AI', path: 'M362 267 H404 V66 H440' },
    { x: 556, y: 120, w: 76, h: 44, label: 'ERP', path: 'M362 267 H420 V142 H556' },
    { x: 446, y: 200, w: 72, h: 44, label: 'Cloud', path: 'M362 267 H404 V222 H446' },
    { x: 548, y: 262, w: 84, h: 52, label: 'CRM', path: 'M362 267 H476 V288 H548' },
    { x: 18, y: 112, w: 92, h: 44, label: 'Payments', path: 'M272 267 H140 V134 H110' },
    { x: 26, y: 222, w: 84, h: 44, label: 'Couriers', path: 'M272 267 H150 V244 H110' },
  ];
}
