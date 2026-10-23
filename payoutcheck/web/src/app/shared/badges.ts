import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FindingStatus, Platform, Severity } from '../core/models';
import { PLATFORM_LABEL, SEVERITY_LABEL, STATUS_LABEL } from './format';

@Component({
  selector: 'app-severity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge sev-{{ value().toLowerCase() }}"><span class="dot" aria-hidden="true"></span>{{ label[value()] }}</span>`,
})
export class SeverityBadge {
  readonly value = input.required<Severity>();
  protected readonly label = SEVERITY_LABEL;
}

@Component({
  selector: 'app-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge st-{{ value().toLowerCase() }}">{{ label[value()] }}</span>`,
})
export class StatusBadge {
  readonly value = input.required<FindingStatus>();
  protected readonly label = STATUS_LABEL;
}

@Component({
  selector: 'app-platform',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge pf-{{ value().toLowerCase() }}">{{ label[value()] }}</span>`,
})
export class PlatformBadge {
  readonly value = input.required<Platform>();
  protected readonly label = PLATFORM_LABEL;
}
