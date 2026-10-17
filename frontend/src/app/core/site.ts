/** Facts about the company used across pages, meta tags and structured data. */
export const SITE = {
  name: 'LiableTech',
  url: 'https://liabletech.com',
  email: 'contact@liabletech.com',
  tagline: 'Reliable by design.',
  positioning:
    'An AI-enabled technology partner that turns complex business problems into reliable digital solutions.',
  logo: 'https://liabletech.com/brand/liabletech-logo-512.png',
  ogImage: 'https://liabletech.com/liabletech-og-image.png',
  replyPromise: 'We reply within one business day.',
} as const;

export function absoluteUrl(path: string): string {
  return path === '/' ? SITE.url + '/' : SITE.url + path;
}
