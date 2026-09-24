import { Injectable, signal } from '@angular/core';

export type Theme = 'dark-green' | 'light';

@Injectable({
    providedIn: 'root'
})
export class ThemeService {
    private readonly THEME_KEY = 'arena-theme';
    theme = signal<Theme>(this.getStoredTheme());

    constructor() {
        this.applyTheme(this.theme());
    }

    toggleTheme() {
        const newTheme: Theme = this.theme() === 'dark-green' ? 'light' : 'dark-green';
        this.theme.set(newTheme);
        localStorage.setItem(this.THEME_KEY, newTheme);
        this.applyTheme(newTheme);
    }

    private applyTheme(theme: Theme) {
        const body = document.getElementsByTagName('body')[0];
        body.classList.remove('dark-green', 'light');
        body.classList.add(theme);
    }

    private getStoredTheme(): Theme {
        const stored = localStorage.getItem(this.THEME_KEY) as Theme;
        return stored || 'light';
    }
}
