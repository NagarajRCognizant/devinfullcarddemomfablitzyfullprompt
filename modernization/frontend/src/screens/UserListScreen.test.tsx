import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UserListScreen } from './UserListScreen';
import { stubFetch, userPage } from '../test/fixtures';

describe('User list screen', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the first page on entry', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/users': { body: userPage } }));

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);

    expect(await screen.findByText('ADMIN001')).toBeInTheDocument();
    expect(screen.getByText('USER0002')).toBeInTheDocument();
  });

  it('browses forward from the last id shown, as PF8 did', async () => {
    const fetchMock = stubFetch({ 'GET /api/users': { body: userPage } });
    vi.stubGlobal('fetch', fetchMock);

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('ADMIN001');
    await userEvent.click(screen.getByRole('button', { name: 'F8 - Forward' }));

    const requested = String(fetchMock.mock.calls[1][0]);
    expect(requested).toContain('direction=NEXT');
    expect(requested).toContain(`userId=${userPage.lastUserId}`);
  });

  it('browses backward from the first id shown, as PF7 did', async () => {
    const fetchMock = stubFetch({ 'GET /api/users': { body: userPage } });
    vi.stubGlobal('fetch', fetchMock);

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('ADMIN001');
    await userEvent.click(screen.getByRole('button', { name: 'F7 - Backward' }));

    const requested = String(fetchMock.mock.calls[1][0]);
    expect(requested).toContain('direction=PREVIOUS');
    expect(requested).toContain(`userId=${userPage.firstUserId}`);
  });

  it('keeps the page on display at the boundaries, as SEND-ERASE-NO did', async () => {
    const fetchMock = stubFetch({ 'GET /api/users': { body: userPage } });
    vi.stubGlobal('fetch', fetchMock);

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('ADMIN001');

    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          users: [],
          firstUserId: null,
          lastUserId: null,
          pageNumber: 1,
          atTop: false,
          message: 'You are already at the bottom of the page...',
        }),
        { status: 200, headers: { 'content-type': 'application/json' } },
      ),
    );
    await userEvent.click(screen.getByRole('button', { name: 'F8 - Forward' }));

    expect(await screen.findByRole('status')).toHaveTextContent(
      'You are already at the bottom of the page...',
    );
    expect(screen.getByText('ADMIN001')).toBeInTheDocument();
    expect(screen.getByText('USER0002')).toBeInTheDocument();
  });

  it('erases the page when a search finds no user', async () => {
    const fetchMock = stubFetch({ 'GET /api/users': { body: userPage } });
    vi.stubGlobal('fetch', fetchMock);

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('ADMIN001');

    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          users: [],
          firstUserId: null,
          lastUserId: null,
          pageNumber: 1,
          atTop: true,
          message: 'You are at the top of the page...',
        }),
        { status: 200, headers: { 'content-type': 'application/json' } },
      ),
    );
    await userEvent.type(screen.getByLabelText('User ID'), 'ZZZZZZZZ');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('status')).toHaveTextContent('You are at the top of the page...');
    expect(screen.queryByText('ADMIN001')).not.toBeInTheDocument();
  });

  it('transfers to update on U and to delete on D', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/users': { body: userPage } }));
    const select = vi.fn();

    render(<UserListScreen onSelect={select} onExit={() => {}} />);
    await screen.findByText('ADMIN001');
    await userEvent.type(screen.getByLabelText('Selection for ADMIN001'), 'u');
    await userEvent.click(screen.getAllByRole('button', { name: 'Enter' })[0]);

    expect(select).toHaveBeenCalledWith('COUSR02C', 'ADMIN001');

    await userEvent.type(screen.getByLabelText('Selection for USER0002'), 'D');
    await userEvent.click(screen.getAllByRole('button', { name: 'Enter' })[1]);

    expect(select).toHaveBeenLastCalledWith('COUSR03C', 'USER0002');
  });

  it('refuses any other selection character', async () => {
    vi.stubGlobal('fetch', stubFetch({ 'GET /api/users': { body: userPage } }));

    render(<UserListScreen onSelect={() => {}} onExit={() => {}} />);
    await screen.findByText('ADMIN001');
    await userEvent.type(screen.getByLabelText('Selection for ADMIN001'), 'X');
    await userEvent.click(screen.getAllByRole('button', { name: 'Enter' })[0]);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Invalid selection. Valid values are U and D',
    );
  });
});
