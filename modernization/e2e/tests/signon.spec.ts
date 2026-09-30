import { expect, test } from '@playwright/test';
import {
  adminUser,
  enrol,
  enrollingUser,
  lockoutUser,
  regularUser,
  resetEnrolment,
  signOn,
  submitOtp,
  submitPassword,
  totp,
} from './realm';

/**
 * COSGN00C, as the realm performs it: the password and the one-time code are asked for on the realm's
 * pages, and the type of the USRSEC row decides which menu follows, which is the routing the source
 * did after READ-USER-SEC-FILE.
 */
test.describe('sign-on through the realm', () => {
  test('an administrator reaches the admin menu after both factors', async ({ page }) => {
    await signOn(page, adminUser());

    await expect(page.getByRole('heading', { name: 'Admin Menu' })).toBeVisible();
    await expect(page.getByText('Prog: COADM01C')).toBeVisible();
  });

  test('a regular user reaches the main menu and not the admin one', async ({ page }) => {
    await signOn(page, regularUser());

    await expect(page.getByRole('heading', { name: 'Main Menu' })).toBeVisible();
    await expect(page.getByText('Prog: COMEN01C')).toBeVisible();
  });

  test('the sign-on page has no password field of its own', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('input[type="password"]')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Sign on' })).toBeVisible();
  });

  /**
   * A user the identity service has just created has a password and no authenticator, and the realm does
   * not let it past sign-on until it has one. COSGN00C had no equivalent step; this is the one part of
   * the sign-on the modernised application adds rather than reproduces.
   */
  test('a user without an authenticator has to enrol one before reaching a screen', async ({
    page,
  }) => {
    const user = enrollingUser();
    // Enrolling is something a user does once, so the user is first put back the way the identity
    // service creates one - the reset an administrator performs for a lost authenticator.
    await resetEnrolment(user.username);
    await page.goto('/');
    await page.getByRole('button', { name: 'Sign on' }).click();
    await submitPassword(page, user);

    await expect(page.getByRole('heading', { name: /mobile authenticator setup/i })).toBeVisible();

    await enrol(page, user);

    // The type of the USRSEC row still decides the menu, enrolled a moment ago or years ago.
    await expect(page.getByRole('heading', { name: 'Main Menu' })).toBeVisible();
  });

  /**
   * The refusals are driven as user0002, whose only purpose is to be refused: the realm counts failed
   * attempts per user and locks the account for a while, so refusing one of the two users the journeys
   * sign on as would leave those failing on a lockout instead of on what they assert. They are one test
   * for the same reason: the first refusal already starts the count, and a second test signing the same
   * user on would be refused on the lockout rather than on the code it keyed.
   */
  test('wrong codes are refused and in the end lock the account out', async ({ page }) => {
    const user = lockoutUser();
    await page.goto('/');
    await page.getByRole('button', { name: 'Sign on' }).click();
    await submitPassword(page, user);

    await submitOtp(page, '000000');

    // The realm stays on its own page and says so; the screens are never reached.
    await expect(page.locator('#otp')).toBeVisible();
    await expect(
      page.locator('#input-error-otp-code, .kc-feedback-text, #input-error').first(),
    ).toContainText(/invalid/i);
    await expect(page.getByRole('heading', { name: 'Admin Menu' })).toHaveCount(0);

    // A code of this user's own authenticator is refused too once its window has passed: ten steps back
    // is far outside the one step of look-ahead the realm's OTP policy allows.
    await submitOtp(page, totp(user.totpSecret, Math.floor(Date.now() / 1000 / 30) - 10));
    await expect(page.locator('#otp')).toBeVisible();

    // Wrong codes until the realm's brute-force detection has counted more than the five failures its
    // policy allows. The form of the flow in hand keeps asking for a code; what the lockout changes is
    // the next sign-on.
    for (let attempt = 0; attempt < 5; attempt += 1) {
      await submitOtp(page, '000000');
    }

    // The account is now locked out for a while, so a fresh attempt is refused on the password the realm
    // accepted a moment ago and the code an authenticator would show is never even asked for.
    await page.goto('/');
    await page.getByRole('button', { name: 'Sign on' }).click();
    await submitPassword(page, user);
    await expect(
      page.locator('#input-error, #input-error-username, .kc-feedback-text').first(),
    ).toContainText(/invalid/i);
    await expect(page.locator('#otp')).toHaveCount(0);
  });

  test('the session survives a reload and signing off ends it at the realm', async ({ page }) => {
    await signOn(page, adminUser());
    await expect(page.getByRole('heading', { name: 'Admin Menu' })).toBeVisible();

    await page.reload();
    await expect(page.getByRole('heading', { name: 'Admin Menu' })).toBeVisible();

    await page.getByRole('button', { name: /sign off/i }).click();
    await expect(page.getByRole('heading', { name: 'Sign on' })).toBeVisible();

    // Signing off ended the realm's session too, so the next attempt asks for both factors again.
    await page.getByRole('button', { name: 'Sign on' }).click();
    await expect(page.locator('#password')).toBeVisible();
  });
});
