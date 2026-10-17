// Runs after `ng build` (see package.json "build"). Prepares the pre-rendered site for Cloudflare Pages:
//   1. about/index.html -> about.html, so pages are served at /about with no redirect to /about/
//   2. 404/index.html -> 404.html, which Pages returns (with a 404 status) for unknown URLs
//   3. removes index.csr.html, the client-only fallback that static hosting does not need
//   4. writes sitemap.xml listing every indexable page
import { existsSync, readdirSync, readFileSync, renameSync, rmdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const SITE = 'https://liabletech.com';
const out = join(import.meta.dirname, '..', 'dist', 'frontend', 'browser');
const skip = new Set(['ecommerce', 'fonts', 'brand']);

function walk(dir) {
  const pages = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      if (dir === out && skip.has(name)) continue;
      pages.push(...walk(full));
    } else if (name === 'index.html' && dir !== out) {
      pages.push(full);
    }
  }
  return pages;
}

const pages = walk(out).sort((a, b) => b.length - a.length); // deepest first
const routes = ['/'];
for (const file of pages) {
  const dir = file.slice(0, -'/index.html'.length);
  renameSync(file, dir + '.html');
  if (readdirSync(dir).length === 0) rmdirSync(dir);
  const route = '/' + relative(out, dir).split(sep).join('/');
  if (route !== '/404') routes.push(route);
}
rmSync(join(out, 'index.csr.html'), { force: true });

for (const route of routes) {
  const file = route === '/' ? join(out, 'index.html') : join(out, route.slice(1) + '.html');
  if (!existsSync(file)) throw new Error(`missing page for ${route}`);
  if (/<meta name="robots" content="noindex/.test(readFileSync(file, 'utf8'))) {
    routes.splice(routes.indexOf(route), 1);
  }
}

const today = new Date().toISOString().slice(0, 10);
const priority = (r) => (r === '/' ? '1.0' : /^\/(services|products|contact)$/.test(r) ? '0.9' : r === '/privacy' ? '0.3' : '0.8');
const urls = routes
  .sort()
  .map((r) => `  <url><loc>${SITE}${r === '/' ? '/' : r}</loc><lastmod>${today}</lastmod><priority>${priority(r)}</priority></url>`)
  .join('\n');
writeFileSync(
  join(out, 'sitemap.xml'),
  `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
);

console.log(`postbuild: ${pages.length} pages flattened, sitemap.xml with ${routes.length} URLs`);
