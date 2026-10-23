import type { Language, OrderLine } from './db.ts';

/** Drops the noise from quantities: 2.50 -> "2.5", 3 -> "3". */
export function formatQuantity(q: number | null): string {
  if (q === null) {
    return '?';
  }
  return String(Math.round(q * 1000) / 1000);
}

const TEMPLATES: Record<Language, { greeting: (shop: string) => string; ask: string }> = {
  hi: {
    greeting: (shop) => `नमस्ते ${shop}, आपका ऑर्डर मिला:`,
    ask: 'सही है तो "हाँ" भेजें, कुछ बदलना हो तो बताएं।',
  },
  mr: {
    greeting: (shop) => `नमस्कार ${shop}, तुमची ऑर्डर मिळाली:`,
    ask: 'बरोबर असेल तर "हो" पाठवा, काही बदल असल्यास कळवा.',
  },
  en: {
    greeting: (shop) => `Hello ${shop}, we have your order:`,
    ask: 'Reply "yes" if this is right, or tell us what to change.',
  },
};

/** The message read back to the shopkeeper, in their language, so they can catch mistakes. */
export function confirmationText(shopName: string, language: Language, lines: OrderLine[]): string {
  const t = TEMPLATES[language];
  const body = lines.map((l, i) => `${i + 1}. ${l.itemName} - ${formatQuantity(l.quantity)} ${l.unit}`.trimEnd());
  return [t.greeting(shopName), ...body, '', t.ask].join('\n');
}

/**
 * A wa.me link that opens WhatsApp with the confirmation typed in, ready for staff to send.
 * Ten-digit numbers are taken as Indian mobiles. Returns null when there is no usable number.
 */
export function whatsappLink(phone: string, text: string): string | null {
  let digits = phone.replace(/\D/g, '');
  if (digits.length === 11 && digits.startsWith('0')) {
    digits = digits.slice(1);
  }
  if (digits.length === 10) {
    digits = '91' + digits;
  }
  if (digits.length < 11 || digits.length > 15) {
    return null;
  }
  return `https://wa.me/${digits}?text=${encodeURIComponent(text)}`;
}
