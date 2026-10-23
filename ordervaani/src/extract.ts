import Anthropic from '@anthropic-ai/sdk';
import { betaZodOutputFormat } from '@anthropic-ai/sdk/helpers/beta/zod';
import { z } from 'zod';

import type { Item, OrderLine } from './db.ts';

export type ExtractInput =
  | { kind: 'text'; shopName: string; text: string; fromVoice: boolean }
  | { kind: 'image'; shopName: string; mediaType: ImageMediaType; base64: string };

export type ImageMediaType = 'image/jpeg' | 'image/png' | 'image/gif' | 'image/webp';
export const IMAGE_MEDIA_TYPES: ImageMediaType[] = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

export interface Extraction {
  /** For photos, the list as the AI read it, kept so staff can compare. */
  transcript: string;
  lines: OrderLine[];
}

/** Turns one order (text or photo) into order lines matched to the catalogue. */
export interface Extractor {
  extract(input: ExtractInput, catalogue: Item[]): Promise<Extraction>;
}

const ExtractionSchema = z.object({
  transcript: z
    .string()
    .describe('For a photo: every order line you can read, one per line, as written. For text input: an empty string.'),
  lines: z.array(
    z.object({
      heard: z.string().describe('The words for this line exactly as the shopkeeper said or wrote them.'),
      catalogue_id: z.number().int().nullable().describe('id of the matching catalogue item, or null if no item clearly matches.'),
      item_name: z.string().describe('The catalogue item name when matched; otherwise the item in plain English.'),
      quantity: z.number().nullable().describe('The quantity as a number, or null if none was given or it cannot be read.'),
      unit: z.string().describe('Unit for the quantity, normally the catalogue unit of the matched item.'),
      needs_check: z.boolean().describe('true when staff should check this line before it is confirmed.'),
      note: z.string().describe('Short reason in English when needs_check is true; otherwise an empty string.'),
    }),
  ),
});

const INSTRUCTIONS = `You take orders for a wholesale distributor in India. Retail shopkeepers send orders as WhatsApp voice notes (already converted to text, which may contain speech-recognition mistakes), photos of handwritten lists, or text messages, usually in Hindi, Marathi, English or a mix, in Devanagari or Latin script.

Turn the order into one line per item the shopkeeper wants, in the order they gave them.

Matching:
- Match each item to the distributor's catalogue below, using names, aliases, spelling variants, transliterations and likely speech-recognition errors. Use the catalogue item's id and exact name.
- Only match when you are confident. If no catalogue item clearly fits, set catalogue_id to null, write the item in plain English and set needs_check.
- If one word could mean two catalogue items (for example two pack sizes of the same product), pick the likelier one and set needs_check, naming the alternative in the note.

Quantities and units:
- Convert spoken or written amounts to numbers: aadha/अर्धा = 0.5, paav/पाव = 0.25, sava/सवा = 1.25, dedh/दीड/डेढ़ = 1.5, dhai/अडीच/ढाई = 2.5, saade/साडे N = N + 0.5, paune/पावणे N = N - 0.25, darjan/डझन = 12 pieces. Numbers may be spoken in Hindi or Marathi.
- kilo/किलो = kg. peti/पेटी usually means a carton or case; bori/गोणी a sack; dabba/डबा a tin.
- Use the matched item's catalogue unit. If the shopkeeper used a different unit that you cannot convert with certainty (for example "2 bori" of an item sold by kg), keep their quantity and unit and set needs_check, explaining the mismatch.
- If no quantity was given, set quantity to null and needs_check.

Never add items the shopkeeper did not ask for. Ignore greetings, payment talk and anything that is not an order line. If they cancel or change an item within the same note, keep only the final version. If the note contains no order at all, return no lines.`;

function catalogueText(catalogue: Item[]): string {
  const rows = catalogue.map((i) => `${i.id} | ${i.name} | ${i.unit} | ${i.aliases}`);
  return `Catalogue (id | name | unit | other names):\n${rows.join('\n')}`;
}

/** Calls Claude with structured outputs, so the reply always parses into order lines. */
export class ClaudeExtractor implements Extractor {
  private readonly client: Anthropic;
  private readonly model: string;

  constructor(options: { client?: Anthropic; model?: string } = {}) {
    this.client = options.client ?? new Anthropic();
    this.model = options.model ?? 'claude-opus-5-5';
  }

  async extract(input: ExtractInput, catalogue: Item[]): Promise<Extraction> {
    const ask =
      input.kind === 'image'
        ? `Order from ${input.shopName}: a photo of a handwritten list. Read it and return the order lines.`
        : `Order from ${input.shopName}, ${input.fromVoice ? 'a voice note converted to text' : 'a text message'}:\n\n${input.text}`;
    const content: Anthropic.Beta.BetaContentBlockParam[] =
      input.kind === 'image'
        ? [
            { type: 'image', source: { type: 'base64', media_type: input.mediaType, data: input.base64 } },
            { type: 'text', text: ask },
          ]
        : [{ type: 'text', text: ask }];

    const response = await this.client.beta.messages.parse({
      model: this.model,
      max_tokens: 16000,
      betas: ['server-side-fallback-2026-07-01'],
      fallbacks: 'default',
      output_config: { effort: 'medium', format: betaZodOutputFormat(ExtractionSchema) },
      // The instructions and catalogue are the same for every order, so they are cached.
      system: [
        { type: 'text', text: INSTRUCTIONS },
        { type: 'text', text: catalogueText(catalogue), cache_control: { type: 'ephemeral' } },
      ],
      messages: [{ role: 'user', content }],
    });

    if (response.stop_reason === 'refusal') {
      throw new Error('The AI declined to read this order. Enter the lines by hand.');
    }
    if (response.stop_reason === 'max_tokens' || !response.parsed_output) {
      throw new Error('The AI reply was cut off or could not be read. Press Read again, or enter the lines by hand.');
    }

    const known = new Set(catalogue.map((i) => i.id));
    return {
      transcript: response.parsed_output.transcript,
      lines: response.parsed_output.lines.map((l) => {
        const matched = l.catalogue_id !== null && known.has(l.catalogue_id);
        return {
          itemId: matched ? l.catalogue_id : null,
          itemName: l.item_name,
          quantity: l.quantity,
          unit: l.unit,
          heard: l.heard,
          needsCheck: l.needs_check || !matched || l.quantity === null,
          note: l.note || (matched ? '' : 'Not matched to a catalogue item'),
        };
      }),
    };
  }
}

/** A message staff can act on, instead of a stack trace. */
export function describeAiError(error: unknown): string {
  if (error instanceof Anthropic.AuthenticationError) {
    return 'The Anthropic API key was rejected. Check ANTHROPIC_API_KEY in .env and restart the app.';
  }
  if (error instanceof Anthropic.PermissionDeniedError) {
    return 'The Anthropic API key is not allowed to use this model. Check the key or set ORDERVAANI_MODEL.';
  }
  if (error instanceof Anthropic.RateLimitError) {
    return 'The AI service is busy (rate limit). Wait a minute and press Read again.';
  }
  if (error instanceof Anthropic.BadRequestError) {
    return `The AI service rejected the request: ${error.message}`;
  }
  if (error instanceof Anthropic.APIConnectionError) {
    return 'Could not reach the AI service. Check the internet connection and press Read again.';
  }
  if (error instanceof Anthropic.APIError) {
    return `The AI service failed (${error.status ?? 'no status'}). Press Read again in a moment.`;
  }
  return error instanceof Error ? error.message : String(error);
}
