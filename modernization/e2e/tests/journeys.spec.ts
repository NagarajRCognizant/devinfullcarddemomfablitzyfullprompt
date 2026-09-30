import { expect, test } from '@playwright/test';
import { accessToken, adminUser, realmHasUser, regularUser, signOn } from './realm';

/**
 * The transactions behind the menus, driven with a realm-issued token: the screens are unchanged, so
 * what these prove is that the token the realm mints is accepted by every service the journeys touch
 * and that the ADMIN role still separates the user administration from the rest.
 */
test.describe('journeys with a realm-issued token', () => {
  test('an administrator lists the users of USRSEC', async ({ page }) => {
    await signOn(page, adminUser());

    await page.getByLabel(/option/i).fill('1');
    await page.getByRole('button', { name: /enter/i }).click();

    await expect(page.getByRole('heading', { name: 'List Users' })).toBeVisible();
    await expect(page.getByText('ADMIN001')).toBeVisible();
  });

  test('an administrator adds a user, which the realm is given one too', async ({ page }) => {
    // COUSR01C writes USRSEC, and the user it writes has to be able to sign on, which means the realm
    // needs an account for it as well: the service account of carddemo-identity-admin provisions one
    // over the realm's admin API. The realm refuses that call unless the roles the bootstrap grants
    // that account are in its token, so this is what a realm the client is under-scoped in fails on.
    const userId = `E2E${String(Date.now()).slice(-5)}`;
    await signOn(page, adminUser());

    await page.getByLabel(/option/i).fill('2');
    await page.getByRole('button', { name: /enter/i }).click();
    await expect(page.getByRole('heading', { name: 'Add User' })).toBeVisible();
    await page.getByLabel('First Name').fill('EVELYN');
    await page.getByLabel('Last Name').fill('REED');
    await page.getByLabel('User ID').fill(userId);
    await page.getByLabel('Password').fill('PASSWORD');
    await page.getByLabel('User Type').fill('U');
    await page.getByRole('button', { name: /^enter$/i }).click();

    await expect(page.getByText(new RegExp(`${userId}.*added`, 'i'))).toBeVisible();
    expect(await realmHasUser(userId.toLowerCase())).toBe(true);

    // Deleting it puts the realm back as it was, which is the other half of the mirroring.
    await page.getByRole('button', { name: /F3 - Back/i }).click();
    await page.getByLabel(/option/i).fill('4');
    await page.getByRole('button', { name: /enter/i }).click();
    await expect(page.getByRole('heading', { name: 'Delete User' })).toBeVisible();
    await page.getByLabel('User ID').fill(userId);
    await page.getByRole('button', { name: 'Fetch' }).click();
    await expect(page.getByText('REED')).toBeVisible();
    await page.getByRole('button', { name: /F5 - Delete/i }).click();

    await expect(page.getByText(new RegExp(`${userId}.*deleted`, 'i'))).toBeVisible();
    expect(await realmHasUser(userId.toLowerCase())).toBe(false);
  });

  test('a regular user views an account', async ({ page }) => {
    await signOn(page, regularUser());

    await page.getByRole('button', { name: /01\. Account View/i }).click();

    await expect(page.getByRole('heading', { name: 'View Account' })).toBeVisible();
    await page.getByLabel(/account number/i).fill('00000000011');
    await page.getByRole('button', { name: /search/i }).click();

    await expect(page.getByText(/active/i).first()).toBeVisible();
  });

  test('a regular user cannot reach the user administration', async ({ page }) => {
    await signOn(page, regularUser());

    await expect(page.getByRole('heading', { name: 'Main Menu' })).toBeVisible();

    // The main menu of COMEN01C never offers the COUSR* options; asked for anyway, with this user's
    // own token, the answer is a refusal, because /api/users/** requires the ADMIN realm role.
    const forbidden = await page.request.get('/api/users?page=0', {
      headers: { Authorization: `Bearer ${await accessToken(page)}` },
    });
    expect(forbidden.status()).toBe(403);

    // And without a token at all it is not even a question of roles.
    const anonymous = await page.request.get('/api/users?page=0');
    expect(anonymous.status()).toBe(401);
  });
});
