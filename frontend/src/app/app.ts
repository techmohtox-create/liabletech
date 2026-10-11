import { Component, inject } from '@angular/core';

import { LandingService } from './core/landing.service';
import { Approach } from './sections/approach';
import { Contact } from './sections/contact';
import { Footer } from './sections/footer';
import { Header } from './sections/header';
import { Hero } from './sections/hero';
import { Process } from './sections/process';
import { Services } from './sections/services';

@Component({
  selector: 'app-root',
  imports: [Header, Hero, Services, Approach, Process, Contact, Footer],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly landing = inject(LandingService);

  protected readonly content = this.landing.content;
  protected readonly failed = this.landing.failed;

  constructor() {
    this.landing.load();
  }

  protected reload(): void {
    this.landing.load();
  }
}
