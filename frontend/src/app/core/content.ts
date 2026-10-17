/**
 * All marketing copy lives here so it is rendered into static HTML at build time (good for
 * search engines) and can be edited in one place. Never add invented clients, testimonials or
 * metrics: the brand guidelines forbid it.
 */
import { IconName } from '../shared/icon';

export interface Faq {
  q: string;
  a: string;
}

export interface Service {
  slug: string;
  icon: IconName;
  name: string;
  /** The service as it reads mid-sentence, e.g. "Talk to us about AI automation". */
  phrase: string;
  short: string;
  headline: string;
  intro: string;
  outcomes: string[];
  deliverables: { title: string; text: string }[];
  useCases: string[];
  stack: string[];
  faqs: Faq[];
  seoTitle: string;
  seoDescription: string;
}

export const SERVICES: Service[] = [
  {
    slug: 'ai-automation',
    icon: 'ai',
    name: 'AI automation',
    phrase: 'AI automation',
    short: 'Automate repetitive, document-heavy work with AI your team can check and trust.',
    headline: 'AI automation that removes real work, not just adds a chatbot',
    intro:
      'We find the manual steps that cost your team the most hours, then automate them with AI models, business rules and integrations. Wherever accuracy matters, a person reviews the result before it counts.',
    outcomes: [
      'Fewer hours lost to data entry, triage and copying between systems',
      'Faster turnaround on documents, tickets and approvals',
      'AI that works inside the tools your team already uses',
      'A clear record of what the AI did and who approved it',
    ],
    deliverables: [
      { title: 'Automation audit', text: 'A ranked list of the processes worth automating, with effort, risk and payoff for each.' },
      { title: 'Document processing', text: 'Invoices, purchase orders, forms and IDs read, checked and posted to your systems.' },
      { title: 'Assistants on your data', text: 'Answers grounded in your own documents and records, with sources shown.' },
      { title: 'Workflow automation', text: 'Email, ERP, CRM and spreadsheets connected so work moves without copy-paste.' },
      { title: 'Guardrails and monitoring', text: 'Human review steps, logging, and tracking of accuracy and running cost.' },
    ],
    useCases: [
      'Read supplier invoices and post them to accounts',
      'Classify and route support emails and tickets',
      'Draft replies and summaries for your team to approve',
      'Pull data out of PDFs and scanned forms',
    ],
    stack: ['OpenAI and Anthropic models', 'Google Cloud Vertex AI', 'Python', 'Java · Spring Boot', 'Workflow engines'],
    faqs: [
      {
        q: 'Will the AI make mistakes with our data?',
        a: 'It can, so every workflow we build has checks: confidence thresholds, human approval for important steps and logs you can review. We start with processes where an error is cheap to catch.',
      },
      {
        q: 'Do we have to send our data to AI providers?',
        a: 'Only what a task needs, under business terms that exclude training on your data. Where that is not acceptable, models can run inside your own cloud account.',
      },
      {
        q: 'How soon will we see results?',
        a: 'The audit tells you before you commit. A first workflow usually goes live within a few weeks of it, depending on the systems involved.',
      },
    ],
    seoTitle: 'AI automation services for business | LiableTech',
    seoDescription:
      'Automate document processing, ticket triage and repetitive workflows with AI your team can review. Audit first, then production-ready automation with guardrails.',
  },
  {
    slug: 'custom-software',
    icon: 'software',
    name: 'Custom software development',
    phrase: 'custom software',
    short: 'Web apps, portals and internal tools built around how your business actually works.',
    headline: 'Custom software, engineered to be relied on',
    intro:
      'From a first version you can put in front of customers to replacing a system your business has outgrown, one accountable team takes the work from the first workshop to production support.',
    outcomes: [
      'Software shaped around your process, not the other way round',
      'Working software to review every sprint, not a reveal at the end',
      'Code, infrastructure and documentation that you own',
      'A system your own team can take over whenever you choose',
    ],
    deliverables: [
      { title: 'Product discovery', text: 'Workshops, user flows and a clickable prototype before a line of production code.' },
      { title: 'Web applications and portals', text: 'Customer portals, dashboards and internal tools in Angular or React.' },
      { title: 'Back ends and APIs', text: 'Secure, tested services in Java and Spring Boot on PostgreSQL.' },
      { title: 'Mobile-friendly apps', text: 'Responsive progressive web apps that work well on any phone.' },
      { title: 'Legacy modernisation', text: 'Step-by-step replacement of old systems, without a risky big-bang switch.' },
    ],
    useCases: [
      'Customer or dealer portals',
      'Order, inventory and operations systems',
      'Replacing spreadsheets that run critical work',
      'Rebuilding an ageing system on a modern stack',
    ],
    stack: ['Angular', 'React', 'TypeScript', 'Java · Spring Boot', 'PostgreSQL'],
    faqs: [
      {
        q: 'Do you build fixed-price projects?',
        a: 'Yes, when the scope is clear. After discovery you get a written scope, timeline and price. For evolving roadmaps a monthly team is usually better value.',
      },
      {
        q: 'Who owns the code?',
        a: 'You do. The code, the cloud accounts and the documentation are yours, and we can hand everything over to your own team at any time.',
      },
      {
        q: 'Can you take over a project from another vendor?',
        a: 'Yes. We start with a short technical review so you know the state of the code before we commit to timelines.',
      },
    ],
    seoTitle: 'Custom software development company | LiableTech',
    seoDescription:
      'Custom web applications, portals and back-end systems built in Angular, React and Java Spring Boot. Discovery, delivery and support from one accountable team.',
  },
  {
    slug: 'cloud-engineering',
    icon: 'cloud',
    name: 'Cloud engineering',
    phrase: 'cloud engineering',
    short: 'Move to the cloud, automate delivery and keep the monthly bill under control.',
    headline: 'Cloud engineering that cuts the bill and the outages',
    intro:
      'We design, migrate and automate cloud platforms on Google Cloud, AWS and Azure, so releases are routine, environments are reproducible and you know what every rupee of cloud spend buys.',
    outcomes: [
      'Releases that are routine instead of risky',
      'Environments you can rebuild from code in minutes',
      'Lower, predictable cloud spend',
      'Security and backups set up from day one',
    ],
    deliverables: [
      { title: 'Cloud migration', text: 'A planned move of applications and data with rollback at every step.' },
      { title: 'Cloud-native architecture', text: 'Containers, managed databases and services sized for real traffic.' },
      { title: 'CI/CD pipelines', text: 'Automated build, test and deploy on every change.' },
      { title: 'Infrastructure as code', text: 'Terraform and pipelines so every environment is reviewed and repeatable.' },
      { title: 'Cost review', text: 'A line-by-line look at your bill with changes ranked by saving.' },
    ],
    useCases: [
      'Moving from on-premise or shared hosting to the cloud',
      'Cutting a cloud bill that keeps growing',
      'Automating manual deployments',
      'Setting up staging and production properly',
    ],
    stack: ['Google Cloud', 'AWS', 'Azure', 'Docker', 'Kubernetes', 'Terraform', 'GitHub Actions'],
    faqs: [
      {
        q: 'Which cloud do you recommend?',
        a: 'The one that fits your team, compliance needs and budget. We work across Google Cloud, AWS and Azure and will tell you plainly when a simpler setup is enough.',
      },
      {
        q: 'Can you reduce our current cloud bill?',
        a: 'Usually, yes. A cost review shows where the money goes and which changes save the most for the least risk, before anything is touched.',
      },
    ],
    seoTitle: 'Cloud engineering, migration & DevOps services | LiableTech',
    seoDescription:
      'Cloud migration, CI/CD, infrastructure as code and cost optimisation on Google Cloud, AWS and Azure. Reliable releases and a cloud bill you can predict.',
  },
  {
    slug: 'apis-microservices',
    icon: 'api',
    name: 'APIs & microservices',
    phrase: 'APIs and integrations',
    short: 'Connect your ERP, CRM, payments and logistics so data flows without copy-paste.',
    headline: 'APIs and integrations that keep your systems in step',
    intro:
      'We design clean APIs and connect the systems you already run: ERP, CRM, payment gateways, couriers, GST and e-invoicing portals. Data moves once, correctly, with a log of every exchange.',
    outcomes: [
      'One source of truth instead of copies that disagree',
      'No more re-typing orders, invoices or stock between systems',
      'Integrations that fail loudly and recover, not silently',
      'APIs partners can build on, with documentation',
    ],
    deliverables: [
      { title: 'API design', text: 'Well-documented REST APIs with versioning, authentication and rate limits.' },
      { title: 'System integration', text: 'ERP, CRM, payments, couriers, GST and e-invoicing connected end to end.' },
      { title: 'Microservices', text: 'Independent services with clear contracts, where the scale justifies them.' },
      { title: 'Event-driven flows', text: 'Queues and events so systems react in real time and retry safely.' },
      { title: 'Monitoring of every exchange', text: 'Dashboards and alerts for failed or slow integrations.' },
    ],
    useCases: [
      'Sync orders and stock between a store and an ERP',
      'Connect a payment gateway and reconcile settlements',
      'Book shipments and track them automatically',
      'Expose data to partners through a secure API',
    ],
    stack: ['Java · Spring Boot', 'REST & OpenAPI', 'Kafka', 'PostgreSQL', 'API gateways'],
    faqs: [
      {
        q: 'Do we need microservices?',
        a: 'Often not. Many businesses are better served by one well-structured application with good APIs. We recommend microservices only where independent scaling or teams justify the extra moving parts.',
      },
      {
        q: 'Can you integrate with our older systems?',
        a: 'Usually. If a system has no API, we can work with its database, files or exports, and wrap them behind a clean interface.',
      },
    ],
    seoTitle: 'API development & system integration services | LiableTech',
    seoDescription:
      'API design and integration of ERP, CRM, payment gateways, couriers and GST e-invoicing. Microservices and event-driven systems where they pay off.',
  },
  {
    slug: 'site-reliability',
    icon: 'sre',
    name: 'Site reliability',
    phrase: 'site reliability',
    short: 'Monitoring, incident response and performance work that keeps critical systems up.',
    headline: 'Site reliability for systems your business cannot do without',
    intro:
      'We make critical applications observable, fast and resilient: clear reliability targets, alerts that mean something, tested backups and a calm, practised response when something does go wrong.',
    outcomes: [
      'Problems spotted before customers report them',
      'Faster recovery when incidents happen',
      'Pages that stay fast as traffic grows',
      'Backups that have actually been restored in a test',
    ],
    deliverables: [
      { title: 'Observability', text: 'Metrics, logs and traces in one place, with dashboards people actually use.' },
      { title: 'Alerting and on-call', text: 'Alerts tied to user impact, with runbooks for every one.' },
      { title: 'Reliability targets', text: 'Service level objectives agreed with the business and tracked.' },
      { title: 'Performance tuning', text: 'Database, application and front-end work where it moves response times.' },
      { title: 'Backup and recovery', text: 'Backups, restore drills and a written disaster recovery plan.' },
    ],
    useCases: [
      'A store that slows down during sales',
      'An internal system that fails without warning',
      'No alerts until a customer complains',
      'Backups nobody has ever tested',
    ],
    stack: ['Prometheus', 'Grafana', 'OpenTelemetry', 'Google Cloud Operations', 'PagerDuty-style on-call'],
    faqs: [
      {
        q: 'What is site reliability engineering?',
        a: 'Applying engineering to operations: measuring reliability, automating repetitive fixes and learning from incidents so the same failure does not happen twice.',
      },
      {
        q: 'Can you support systems you did not build?',
        a: 'Yes. We start with a reliability review of the system as it is, then agree targets and priorities with you.',
      },
    ],
    seoTitle: 'Site reliability engineering (SRE) services | LiableTech',
    seoDescription:
      'Monitoring, alerting, incident response, performance tuning and tested backups for business-critical applications. Fewer outages, faster recovery.',
  },
  {
    slug: 'managed-support',
    icon: 'support',
    name: 'Managed support',
    phrase: 'managed support',
    short: 'Maintenance, security patching and a team that answers, so your systems keep working.',
    headline: 'Managed support that answers, and owns the fix',
    intro:
      'After launch, someone has to keep software patched, fix what breaks and make the small improvements that keep it useful. Our support plans give you named engineers, agreed response times and a monthly report.',
    outcomes: [
      'Named engineers who already know your system',
      'Security updates applied on a schedule, not after a scare',
      'Small improvements shipped every month',
      'A monthly report on uptime, issues and spend',
    ],
    deliverables: [
      { title: 'Bug fixes and maintenance', text: 'Issues triaged, fixed and released within agreed response times.' },
      { title: 'Security patching', text: 'Dependencies, servers and frameworks kept up to date.' },
      { title: 'Small enhancements', text: 'A monthly allowance for improvements your users ask for.' },
      { title: 'Monitoring and backups', text: 'Uptime, errors and backups checked continuously.' },
      { title: 'Monthly reporting', text: 'What happened, what was fixed and what is coming next.' },
    ],
    useCases: [
      'Software built by a team that has moved on',
      'No in-house developers after launch',
      'Frequent small issues that never get fixed',
      'Out-of-date frameworks with security risks',
    ],
    stack: ['Ticketing and SLAs', 'Monitoring', 'Automated updates', 'Backups'],
    faqs: [
      {
        q: 'Do you offer fixed monthly support plans?',
        a: 'Yes. Plans are sized by the number of systems, the response times you need and the monthly enhancement allowance.',
      },
      {
        q: 'Can you support software built by someone else?',
        a: 'Yes. A short onboarding review gets us familiar with the code and infrastructure before support starts.',
      },
    ],
    seoTitle: 'Managed application support & maintenance | LiableTech',
    seoDescription:
      'Ongoing software maintenance, security patching, bug fixes and monthly enhancements with named engineers and agreed response times.',
  },
];

export type ProductStatus = 'live' | 'soon';

export interface Product {
  slug: string;
  name: string;
  category: string;
  status: ProductStatus;
  tagline: string;
  summary: string;
  audience: string;
  scope: string[];
  seoTitle: string;
  seoDescription: string;
}

export const PRODUCTS: Product[] = [
  {
    slug: 'bazaarflow',
    name: 'BazaarFlow',
    category: 'E-commerce and operations platform',
    status: 'live',
    tagline: 'Sell online and run fulfilment from one platform, with GST built in.',
    summary:
      'A customer storefront and a full operations console on one system: catalogue, inventory, orders, dispatch, payments, GST invoicing, support and reports.',
    audience: 'Brands and distributors in India that sell direct to customers or dealers.',
    scope: [
      'Mobile-first storefront with search, filters, cart and checkout',
      'Order desk with a guarded status workflow',
      'Inventory with an append-only stock ledger',
      'Dispatch queues, shipment booking and packing slips',
      'GST invoices with CGST/SGST or IGST by place of supply',
      'Payments, cash on delivery, refunds and credit notes',
      'Eight business reports with CSV export',
      'Support tickets and eight staff roles with permissions',
    ],
    seoTitle: 'BazaarFlow: e-commerce platform with GST invoicing | LiableTech',
    seoDescription:
      'BazaarFlow is an e-commerce storefront and operations console with GST invoicing, inventory, dispatch, payments and reports. Try the live demo.',
  },
  {
    slug: 'medflow-ai',
    name: 'MedFlow AI',
    category: 'Hospital management',
    status: 'soon',
    tagline: 'Hospital management with AI assistance for the busiest parts of the day.',
    summary:
      'A hospital management system covering the patient journey from registration to discharge, with AI help on documentation that clinicians review and approve.',
    audience: 'Hospitals, clinics and nursing homes.',
    scope: [
      'Patient registration and appointments',
      'Outpatient and inpatient workflows',
      'Billing',
      'Pharmacy and stock',
      'AI-drafted notes and summaries, reviewed by clinicians',
    ],
    seoTitle: 'MedFlow AI hospital management software (coming soon) | LiableTech',
    seoDescription:
      'MedFlow AI is a hospital management system with AI assistance for documentation. Join the waitlist for early access.',
  },
  {
    slug: 'erp',
    name: 'LiableTech ERP',
    category: 'ERP for growing businesses',
    status: 'soon',
    tagline: 'Purchasing, inventory, sales and accounts in one system, built for GST.',
    summary:
      'An ERP for businesses that have outgrown spreadsheets and disconnected tools, bringing purchasing, stock, sales and accounting into one place.',
    audience: 'Manufacturers, distributors and traders.',
    scope: ['Purchasing', 'Inventory and warehouses', 'Sales and invoicing', 'GST-ready accounting', 'Reports and dashboards'],
    seoTitle: 'LiableTech ERP for growing businesses (coming soon) | LiableTech',
    seoDescription:
      'An ERP for purchasing, inventory, sales and GST-ready accounts in one system. Join the waitlist for early access.',
  },
];

/** BazaarFlow modules for the interactive explorer, with deep links into the live demo. */
export const BAZAARFLOW_MODULES = [
  {
    id: 'storefront',
    label: 'Storefront',
    title: 'A storefront built for mobile shoppers',
    points: [
      'Category browsing, search with suggestions, filters and sorting',
      'Product pages with specifications, pack sizes and stock-aware cart',
      'Checkout with saved addresses, online payment or cash on delivery',
      'Order history, delivery timeline, returns and GST invoice download',
    ],
    demoPath: '/ecommerce/shop',
    demoLabel: 'Open the storefront',
  },
  {
    id: 'orders',
    label: 'Orders & dispatch',
    title: 'Every order, from payment to doorstep',
    points: [
      'Order register with filters and a guarded status workflow',
      'Dispatch queues: ready, in transit and exceptions',
      'Shipment booking with tracking numbers and packing slips',
      'Cancellations, returns and refunds handled in one place',
    ],
    demoPath: '/ecommerce/login',
    demoLabel: 'Sign in as the order desk',
  },
  {
    id: 'inventory',
    label: 'Inventory',
    title: 'Stock you can trust',
    points: [
      'Stock reserved at checkout and consumed at dispatch',
      'An append-only ledger of every movement',
      'Adjustments with reasons, and low-stock alerts',
      'Product and category management with live preview',
    ],
    demoPath: '/ecommerce/login',
    demoLabel: 'Sign in as the warehouse',
  },
  {
    id: 'gst',
    label: 'GST & accounts',
    title: 'GST done correctly on every line',
    points: [
      'CGST/SGST or IGST chosen by place of supply',
      'Invoice numbering per Indian financial year',
      'Payments, credit notes and refunds',
      'Downloadable GST invoices as PDF',
    ],
    demoPath: '/ecommerce/login',
    demoLabel: 'Sign in as accounts',
  },
  {
    id: 'insights',
    label: 'Reports & roles',
    title: 'Run the business from one dashboard',
    points: [
      'Dashboard with revenue trend, status mix and top products',
      'Eight reports with CSV export',
      'Support desk with threaded replies',
      'Eight roles and a permission matrix enforced in the API and UI',
    ],
    demoPath: '/ecommerce/login',
    demoLabel: 'Sign in as super admin',
  },
] as const;

export const DEMO_LOGINS = [
  { role: 'Customer', email: 'ganesh@example.com', password: 'farmer@123' },
  { role: 'Super admin', email: 'admin@fieldstone.demo', password: 'demo@123' },
  { role: 'Order desk', email: 'orders@fieldstone.demo', password: 'demo@123' },
  { role: 'Warehouse', email: 'warehouse@fieldstone.demo', password: 'demo@123' },
  { role: 'Accounts', email: 'accounts@fieldstone.demo', password: 'demo@123' },
];

export const PROBLEMS: { id: string; label: string; serviceSlug?: string; productSlug?: string; answer: string }[] = [
  {
    id: 'manual',
    label: 'Too much manual work',
    serviceSlug: 'ai-automation',
    answer: 'We audit where the hours go and automate the worst of it with AI and integrations, with a person approving anything that matters.',
  },
  {
    id: 'outgrown',
    label: 'Our system cannot keep up',
    serviceSlug: 'custom-software',
    answer: 'We replace or modernise it step by step, so the business keeps running while the new system takes over.',
  },
  {
    id: 'sell',
    label: 'We need to sell online',
    productSlug: 'bazaarflow',
    answer: 'BazaarFlow gives you a storefront and the back office to run it, with GST invoicing built in. Try the live demo today.',
  },
  {
    id: 'disconnected',
    label: 'Our tools do not talk to each other',
    serviceSlug: 'apis-microservices',
    answer: 'We connect your ERP, CRM, payments and couriers so data is entered once and stays consistent everywhere.',
  },
  {
    id: 'cloud',
    label: 'Cloud bills up, reliability down',
    serviceSlug: 'cloud-engineering',
    answer: 'We review your setup and bill, automate releases and fix the weak points, starting with what saves the most.',
  },
  {
    id: 'support',
    label: 'Nobody owns our IT day to day',
    serviceSlug: 'managed-support',
    answer: 'A support plan gives you named engineers, agreed response times and monthly improvements.',
  },
];

export const PROCESS = [
  {
    title: 'Discover',
    outcome: 'Goals, constraints and risks mapped',
    description:
      'We talk to the people who use and run your systems to understand the problem before proposing a solution.',
  },
  {
    title: 'Plan',
    outcome: 'Scope, timeline and cost in writing',
    description: 'You get a written plan with milestones and costs, so you know what you are getting and when.',
  },
  {
    title: 'Build',
    outcome: 'Working software every sprint',
    description: 'Short iterations with a demo at the end of each one. You see progress and steer priorities throughout.',
  },
  {
    title: 'Run',
    outcome: 'Monitored, supported, improved',
    description: 'After launch we monitor, support and keep improving, or hand over cleanly to your own team.',
  },
];

export const PRINCIPLES = [
  {
    title: 'One accountable team',
    description: 'The people who scope your project are the people who deliver and support it. No hand-offs, no finger-pointing.',
  },
  {
    title: 'Plain-language commitments',
    description: 'Scope, timeline and cost are written down before work starts, and you hear about a risk the day we see it.',
  },
  {
    title: 'Security by default',
    description: 'Access control, backups and patching are part of every engagement, not a line item added after an incident.',
  },
  {
    title: 'Built to hand over',
    description: 'Documented code, infrastructure you own and no lock-in. You can take the work in-house whenever you choose.',
  },
];

export const ENGAGEMENTS = [
  {
    title: 'Fixed-scope project',
    best: 'A new product, a migration or an automation with a clear goal',
    description: 'Scope, timeline and price agreed in writing up front. You pay for an outcome, not hours.',
  },
  {
    title: 'Dedicated team',
    best: 'An evolving roadmap or an ongoing product',
    description: 'Engineers who work as part of your team, month to month, with the flexibility to scale up or down.',
  },
  {
    title: 'Product plus customisation',
    best: 'Commerce today; hospital management and ERP soon',
    description: 'Start from a LiableTech product and tailor it to your process. Faster and less risky than building from zero.',
  },
  {
    title: 'Managed support',
    best: 'Software that must keep running after launch',
    description: 'Named engineers, agreed response times, patching and monthly improvements for a fixed fee.',
  },
];

export const HOME_FAQS: Faq[] = [
  {
    q: 'What kind of companies do you work with?',
    a: 'Growing businesses and enterprise teams that need software they can depend on, in India and abroad. If a system is important enough that failure costs money, we are a good fit.',
  },
  {
    q: 'How do you price work?',
    a: 'Fixed price for a clearly defined scope, or a monthly fee for a dedicated team or support plan. After a discovery call you get a written estimate before you commit to anything.',
  },
  {
    q: 'Who owns the code and the cloud accounts?',
    a: 'You do. Code, infrastructure and documentation are yours from day one, and we will hand everything over whenever you ask.',
  },
  {
    q: 'Can you work alongside our team or another vendor?',
    a: 'Yes. We regularly fit into existing teams, take over projects from other vendors, or support systems we did not build.',
  },
  {
    q: 'Is BazaarFlow ready to use?',
    a: 'Yes. You can try the full live demo now. We set it up for your brand, connect your payment gateway and courier, and customise it to your process.',
  },
  {
    q: 'Will you sign an NDA?',
    a: 'Yes. If you would like one in place before sharing details, mention it in your message and we will send it over first.',
  },
];

export const NEEDS = [
  ...SERVICES.map((s) => ({ id: s.slug, label: s.name })),
  { id: 'bazaarflow', label: 'BazaarFlow e-commerce' },
  { id: 'medflow-ai', label: 'MedFlow AI (waitlist)' },
  { id: 'erp', label: 'LiableTech ERP (waitlist)' },
  { id: 'other', label: 'Something else' },
];

export const STAGES = ['Just an idea', 'Have a prototype or plan', 'Live system that needs work', 'Replacing an existing system'];
export const TIMELINES = ['As soon as possible', 'Within 1–3 months', 'Within 3–6 months', 'Just exploring'];
export const BUDGETS = ['Under ₹5 lakh', '₹5–15 lakh', '₹15–50 lakh', 'Above ₹50 lakh', 'Not sure yet'];
