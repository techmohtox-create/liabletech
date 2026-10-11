export interface Cta {
  label: string;
  target: string;
}

export interface Hero {
  eyebrow: string;
  headline: string;
  subheadline: string;
  primaryCta: Cta;
  secondaryCta: Cta;
}

export interface ServiceOffering {
  id: string;
  icon: string;
  title: string;
  summary: string;
  capabilities: string[];
}

export interface Principle {
  title: string;
  description: string;
}

export interface ProcessStep {
  title: string;
  outcome: string;
  description: string;
}

export interface TechnologyGroup {
  category: string;
  items: string[];
}

export interface ContactInfo {
  heading: string;
  blurb: string;
  email: string;
}

export interface LandingContent {
  hero: Hero;
  services: ServiceOffering[];
  principles: Principle[];
  process: ProcessStep[];
  technologies: TechnologyGroup[];
  contact: ContactInfo;
}

export interface ContactRequest {
  name: string;
  email: string;
  company: string;
  service: string;
  message: string;
  website: string;
}

export interface ContactResponse {
  reference: string;
  message: string;
}
