import { Pipe, PipeTransform } from '@angular/core';
import { FindingStatus, Platform, Severity } from '../core/models';

const RUPEES = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const RUPEES_ROUND = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const COMPACT = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
const DAY = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });
const DATE = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const STAMP = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

export function inr(value: number | null | undefined, round = false): string {
  if (value === null || value === undefined) return '–';
  return (round ? RUPEES_ROUND : RUPEES).format(value);
}

export function compactInr(value: number): string {
  return '₹' + COMPACT.format(value);
}

/** Dates from the API are ISO strings; plain dates are read as local, not UTC. */
function parse(value: string): Date {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(value + 'T00:00:00') : new Date(value);
}

export function day(value: string | null): string {
  return value ? DAY.format(parse(value)) : '–';
}

export function date(value: string | null): string {
  return value ? DATE.format(parse(value)) : '–';
}

export function stamp(value: string | null): string {
  return value ? STAMP.format(parse(value)) : '–';
}

export function period(start: string, end: string): string {
  return `${day(start)} – ${date(end)}`;
}

export const PLATFORM_LABEL: Record<Platform, string> = { SWIGGY: 'Swiggy', ZOMATO: 'Zomato' };

export const STATUS_LABEL: Record<FindingStatus, string> = {
  OPEN: 'Open',
  DISPUTED: 'Disputed',
  RECOVERED: 'Recovered',
  REJECTED: 'Rejected',
  IGNORED: 'Ignored',
};

export const SEVERITY_LABEL: Record<Severity, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };

@Pipe({ name: 'inr' })
export class InrPipe implements PipeTransform {
  transform(value: number | null | undefined, round = false): string {
    return inr(value, round);
  }
}

@Pipe({ name: 'day' })
export class DayPipe implements PipeTransform {
  transform(value: string | null): string {
    return day(value);
  }
}

@Pipe({ name: 'longDate' })
export class LongDatePipe implements PipeTransform {
  transform(value: string | null): string {
    return date(value);
  }
}

@Pipe({ name: 'stamp' })
export class StampPipe implements PipeTransform {
  transform(value: string | null): string {
    return stamp(value);
  }
}

@Pipe({ name: 'period' })
export class PeriodPipe implements PipeTransform {
  transform(start: string, end: string): string {
    return period(start, end);
  }
}

@Pipe({ name: 'platform' })
export class PlatformPipe implements PipeTransform {
  transform(value: Platform): string {
    return PLATFORM_LABEL[value] ?? value;
  }
}

export const FORMAT_PIPES = [InrPipe, DayPipe, LongDatePipe, StampPipe, PeriodPipe, PlatformPipe] as const;
