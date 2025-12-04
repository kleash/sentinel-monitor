import { Injectable, signal } from '@angular/core';

interface UserProfile {
  name: string;
  roles: string[];
  token?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly userSignal = signal<UserProfile | null>({
    name: 'ops-user',
    roles: ['operator', 'viewer'],
    token: ''
  });

  get user() {
    return this.userSignal.asReadonly();
  }

  setToken(token: string) {
    const current = this.userSignal();
    this.userSignal.set({ ...(current ?? { name: 'ops-user', roles: ['viewer'] }), token });
  }

  logout() {
    this.userSignal.set(null);
  }

  /**
   * Returns the Authorization header value if a token is present.
   */
  authorizationHeader(): string | null {
    const token = this.userSignal()?.token;
    if (!token) {
      return null;
    }
    return `Bearer ${token}`;
  }

  hasRole(role: string): boolean {
    return (this.userSignal()?.roles ?? []).includes(role);
  }
}
