import { createHmac } from 'node:crypto';
import { expect, type Page } from '@playwright/test';

/**
 * The users the suite signs in as and the sign-on it drives.
 *
 * <p>Their TOTP secrets come from the environment, as they do for the bootstrap that enrolled them:
 * a secret in this file would be a committed credential, and the whole point of the second factor is
 * that it is not one. They are the only users the suite can sign in as, because they are the only
 * ones whose secret is known; every other user enrols interactively through CONFIGURE_TOTP.
 */
export interface RealmUser {
  username: string;
  password: string;
  totpSecret: string;
}

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} must be set; see modernization/.env.example`);
  }
  return value;
}

export function adminUser(): RealmUser {
  return {
    username: process.env.E2E_ADMIN_USERNAME ?? 'admin001',
    password: required('E2E_PASSWORD'),
    totpSecret: required('E2E_ADMIN_TOTP_SECRET'),
  };
}

export function regularUser(): RealmUser {
  return {
    username: process.env.E2E_USER_USERNAME ?? 'user0001',
    password: required('E2E_PASSWORD'),
    totpSecret: required('E2E_USER_TOTP_SECRET'),
  };
}

/**
 * The user the refusal tests key wrong codes for.
 *
 * <p>The realm counts failed attempts per user and locks the account for a while, so a test that means
 * to be refused has to be refused on a user of its own; keying wrong codes for user0001 would leave the
 * tests that sign that user on failing on a lockout instead of on what they meant to assert.
 */
export function lockoutUser(): RealmUser {
  return {
    username: process.env.E2E_LOCKOUT_USERNAME ?? 'user0002',
    password: required('E2E_PASSWORD'),
    totpSecret: required('E2E_LOCKOUT_TOTP_SECRET'),
  };
}

/**
 * The user the enrolment test signs on as: a password and no authenticator, as a user the identity
 * service has just created has, so the realm asks it to enrol one before it lets the user through.
 */
export function enrollingUser(): Omit<RealmUser, 'totpSecret'> {
  return {
    username: process.env.E2E_ENROLL_USERNAME ?? 'user0003',
    password: required('E2E_PASSWORD'),
  };
}

/** The realm's own admin API, as an administrator of the realm reaches it. */
async function realmAdmin(): Promise<{ admin: string; auth: { Authorization: string } }> {
  const base = process.env.E2E_KEYCLOAK_URL ?? 'http://127.0.0.1:8088';
  const granted = await fetch(`${base}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: required('KEYCLOAK_ADMIN'),
      password: required('KEYCLOAK_ADMIN_PASSWORD'),
    }),
  });
  if (!granted.ok) {
    throw new Error(`the realm refused the administrator: ${granted.status}`);
  }
  const { access_token: token } = (await granted.json()) as { access_token: string };
  return { admin: `${base}/admin/realms/carddemo`, auth: { Authorization: `Bearer ${token}` } };
}

/** Whether the realm holds a user of this name, which is what a provisioned user is asserted on. */
export async function realmHasUser(username: string): Promise<boolean> {
  const { admin, auth } = await realmAdmin();
  const found = (await (
    await fetch(`${admin}/users?exact=true&username=${username}`, { headers: auth })
  ).json()) as { id: string }[];
  return found.length > 0;
}

/**
 * Puts the enrolling user back the way the identity service creates one: a password, no authenticator
 * and the CONFIGURE_TOTP action outstanding.
 *
 * <p>Enrolment is by nature something a user does once, so the test that drives it would only pass on a
 * freshly bootstrapped realm otherwise. The realm's admin API is what an administrator uses to reset a
 * user who has lost its authenticator (see docs/29), so the test resets the user the same way.
 */
export async function resetEnrolment(username: string): Promise<void> {
  const { admin, auth } = await realmAdmin();

  const found = (await (
    await fetch(`${admin}/users?exact=true&username=${username}`, { headers: auth })
  ).json()) as { id: string }[];
  const id = found[0]?.id;
  if (!id) {
    throw new Error(`${username} is not a user of the realm; run keycloak/bootstrap.sh`);
  }

  const credentials = (await (
    await fetch(`${admin}/users/${id}/credentials`, { headers: auth })
  ).json()) as { id: string; type: string }[];
  for (const credential of credentials.filter((each) => each.type === 'otp')) {
    await fetch(`${admin}/users/${id}/credentials/${credential.id}`, {
      method: 'DELETE',
      headers: auth,
    });
  }
  await fetch(`${admin}/users/${id}`, {
    method: 'PUT',
    headers: { ...auth, 'Content-Type': 'application/json' },
    body: JSON.stringify({ requiredActions: ['CONFIGURE_TOTP'] }),
  });
  await fetch(`${admin}/attack-detection/brute-force/users/${id}`, {
    method: 'DELETE',
    headers: auth,
  });
}

/**
 * The bytes of a base32 key, which is the form the realm's enrolment page shows a new secret in.
 *
 * <p>A person points an authenticator app at the QR code; a test has to read the key beside it and
 * decode it to the bytes the codes are signed with.
 */
export function base32Key(key: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bytes: number[] = [];
  let bits = 0;
  let value = 0;
  for (const character of key.replace(/[\s=]/g, '').toUpperCase()) {
    const index = alphabet.indexOf(character);
    if (index < 0) {
      throw new Error(`${character} is not a base32 character`);
    }
    value = (value << 5) | index;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((value >>> bits) & 0xff);
    }
  }
  return Buffer.from(bytes);
}

/**
 * RFC 6238 with the realm's OTP policy: HmacSHA1, six digits, a 30 second step. The realm accepts one
 * step of look-ahead, so a code computed for a chosen step is what a test needs to hit a given window.
 *
 * <p>The key of a seeded user is the characters of the configured secret, not their base32 decoding:
 * Keycloak stores the secret as text and signs with its bytes, and it is the QR code it shows an
 * authenticator app that is base32 of those same bytes. A key read off the enrolment page is base32 and
 * is passed here already decoded.
 */
export function totp(secret: string | Buffer, step = Math.floor(Date.now() / 1000 / 30)): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const key = typeof secret === 'string' ? Buffer.from(secret, 'utf8') : secret;
  const digest = createHmac('sha1', key).update(counter).digest();
  const offset = digest[digest.length - 1] & 0x0f;
  const binary =
    ((digest[offset] & 0x7f) << 24) |
    (digest[offset + 1] << 16) |
    (digest[offset + 2] << 8) |
    digest[offset + 3];
  return String(binary % 1_000_000).padStart(6, '0');
}

/**
 * The step each user was last signed on with, kept on the worker's global object rather than in a
 * module variable: Playwright loads a spec file in its own module registry, so a variable here would
 * start empty for the second file and lose what the first file had already used.
 */
const stepsUsed: Map<string, number> = ((globalThis as { stepsUsed?: Map<string, number> })
  .stepsUsed ??= new Map());

/**
 * The code to key for a user, waited for until the realm will accept it.
 *
 * <p>Two waits are needed. The realm accepts the code of the current step and of one step ahead but not
 * of the step that has just passed, so a code computed in the last moments of a step is refused by the
 * time the form is submitted; and its OTP policy does not allow a code to be used twice, so a second
 * sign-on within the same step would be refused as a replay. An authenticator app is read by a person,
 * who sees the countdown and does not sign on twice in half a minute; a test has to wait for both.
 */
async function freshCode(user: { username: string; totpSecret: string | Buffer }): Promise<string> {
  for (;;) {
    const step = Math.floor(Date.now() / 1000 / 30);
    const remaining = 30 - (Math.floor(Date.now() / 1000) % 30);
    if (remaining >= 5 && stepsUsed.get(user.username) !== step) {
      stepsUsed.set(user.username, step);
      return totp(user.totpSecret, step);
    }
    await new Promise((resolve) => setTimeout(resolve, remaining * 1000 + 500));
  }
}

/**
 * The access token the page holds, as a call the screens make would carry it.
 *
 * <p>It is read out of the session storage the token is kept in, which is where the page itself reads it
 * from; the callback writes it there a moment after the realm redirects back, so it is waited for.
 */
export async function accessToken(page: Page): Promise<string> {
  let token: string | null = null;
  await expect
    .poll(async () => {
      token = await page.evaluate(() =>
        Object.keys(window.sessionStorage)
          .map((key) => window.sessionStorage.getItem(key))
          .filter((stored): stored is string => stored !== null && stored.includes('access_token'))
          .map((stored) => (JSON.parse(stored) as { access_token?: string }).access_token)
          .find((value): value is string => typeof value === 'string') ?? null,
      );
      return token;
    })
    .not.toBeNull();
  return token ?? '';
}

/** The realm's login page, which the screens redirect to; there is no password field of our own. */
export async function submitPassword(
  page: Page,
  user: Omit<RealmUser, 'totpSecret'>,
): Promise<void> {
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await page.click('#kc-login');
}

export async function submitOtp(page: Page, code: string): Promise<void> {
  await page.fill('#otp', code);
  await page.click('#kc-login');
}

/**
 * Enrols an authenticator on the realm's setup page and signs the user on with it.
 *
 * <p>The page offers the new secret as a barcode for an app to scan, and behind "Unable to scan?" as the
 * base32 key the app would have read from it. The test takes that key, signs a code with it and keys
 * that, which is what the person holding the app does. It returns the key so that the caller can sign
 * the same user on again.
 */
export async function enrol(page: Page, user: { username: string }): Promise<Buffer> {
  await page.locator('#mode-manual').click();
  const key = base32Key(await page.locator('#kc-totp-secret-key').innerText());
  await page.fill('#totp', await freshCode({ username: user.username, totpSecret: key }));
  await page.locator('#kc-form-buttons button[type="submit"], #saveTOTPBtn').first().click();
  await page.waitForURL((url) => url.port === '5173' || url.hostname === '127.0.0.1');
  return key;
}

/** Password and then one-time code, the two steps of the realm's browser flow. */
export async function signOn(page: Page, user: RealmUser): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign on' }).click();
  await page.waitForURL(/\/realms\/carddemo\/protocol\/openid-connect\/auth/);
  await submitPassword(page, user);
  await expect(page.locator('#otp')).toBeVisible();
  // A code can still be refused if the step turns between computing it and the realm reading it, which
  // a person would answer by keying the next code the app shows; one further code is keyed for that.
  for (const attempt of [1, 2]) {
    await submitOtp(page, await freshCode(user));
    if (!(await page.locator('#otp').isVisible())) {
      break;
    }
    expect(attempt, `${user.username} was refused twice: ${await page.locator('body').innerText()}`)
      .toBe(1);
  }
  await page.waitForURL((url) => url.port === '5173' || url.hostname === '127.0.0.1');
}
